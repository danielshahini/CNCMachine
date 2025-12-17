package com.prosysopc.ua.samples.agent;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.JSONObject;

import java.sql.*;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;


public class Timescale {

    private static final String JDBC_URL  = "jdbc:postgresql://timescaledb:5432/mydb";
    private static final String JDBC_USER = "daniel";
    private static final String JDBC_PWD  = "daniel";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PWD)) {
            System.out.println("Connected to TimescaleDB");

            initSchema(conn);

            KafkaConsumer<String, String> consumer = createConsumer();
            consumer.subscribe(Collections.singletonList("eventsData"));
            System.out.println("Subscribed to Kafka topic: eventsData");

            runConsumerLoop(conn, consumer);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void initSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // Basis-Tabelle für Events
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cnc_events (
                        time TIMESTAMPTZ NOT NULL,
                        machine_id TEXT NOT NULL,
                        event_type TEXT NOT NULL CHECK (event_type IN (
                            'CYCLE_START',
                            'CYCLE_PROGRESS',
                            'CYCLE_COMPLETE',
                            'PHASE_CHANGE',
                            'QUALITY_MEASUREMENT',
                            'TOOL_WEAR',
                            'DIMENSION_DRIFT'
                        )),
                        cycle_id TEXT NOT NULL DEFAULT 'N/A',
                        phase TEXT,
                        spindle_load DOUBLE PRECISION,
                        surface_finish DOUBLE PRECISION,
                        tool_life_remaining DOUBLE PRECISION,
                        dimension_error DOUBLE PRECISION,
                        progress_percent DOUBLE PRECISION,
                        plant TEXT,
                        workstation TEXT,
                        order_batch TEXT,
                        material TEXT,
                        quality_mode TEXT,
                        PRIMARY KEY (time, machine_id, event_type, cycle_id)
                    );
                    """);

            st.execute("SELECT create_hypertable('cnc_events', 'time', if_not_exists => TRUE);");

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cnc_events_time_desc ON cnc_events (time DESC);");

            st.execute("""
                    SELECT add_retention_policy(
                        'cnc_events',
                        INTERVAL '60 days',
                        if_not_exists => TRUE
                    );
                    """);

            System.out.println("Base table & hypertable ready.");

            st.executeUpdate("""
                    CREATE MATERIALIZED VIEW IF NOT EXISTS hourly_event_counts
                    WITH (timescaledb.continuous) AS
                    SELECT
                        time_bucket('1 hour', time) AS bucket,
                        machine_id,
                        event_type,
                        COUNT(*) AS events
                    FROM cnc_events
                    GROUP BY bucket, machine_id, event_type
                    WITH NO DATA;
                    """);

            st.execute("CALL refresh_continuous_aggregate('hourly_event_counts', NULL, NULL);");

            // Continuous Aggregate: täglicher Tool-Wear-Trend
            st.executeUpdate("""
                    CREATE MATERIALIZED VIEW IF NOT EXISTS daily_tool_wear
                    WITH (timescaledb.continuous) AS
                    SELECT
                        time_bucket('1 day', time) AS bucket,
                        machine_id,
                        AVG(tool_life_remaining) AS avg_tool_life
                    FROM cnc_events
                    WHERE tool_life_remaining IS NOT NULL
                    GROUP BY bucket, machine_id
                    WITH NO DATA;
                    """);

            st.execute("CALL refresh_continuous_aggregate('daily_tool_wear', NULL, NULL);");

            // Policies für Aggregates
            st.execute("""
                    SELECT add_continuous_aggregate_policy(
                        'hourly_event_counts',
                        start_offset => INTERVAL '7 days',
                        end_offset   => INTERVAL '1 hour',
                        schedule_interval => INTERVAL '30 minutes'
                    );
                    """);

            st.execute("""
                    SELECT add_continuous_aggregate_policy(
                        'daily_tool_wear',
                        start_offset => INTERVAL '90 days',
                        end_offset   => INTERVAL '1 day',
                        schedule_interval => INTERVAL '12 hours'
                    );
                    """);

            System.out.println("Continuous aggregates ready.");
        }
    }

    // -------------------------------------------------------------------------
    // Kafka Consumer
    // -------------------------------------------------------------------------

    private static KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "redpanda_broker:9092");
        props.put("group.id", "timescale-agent-group-v2");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "latest");
        return new KafkaConsumer<>(props);
    }

    private static void runConsumerLoop(Connection conn, KafkaConsumer<String, String> consumer) {
        System.out.println("Start polling loop ...");

        final String insertSql = """
                INSERT INTO cnc_events (
                    time,
                    machine_id,
                    event_type,
                    cycle_id,
                    phase,
                    spindle_load,
                    surface_finish,
                    tool_life_remaining,
                    dimension_error,
                    progress_percent,
                    plant,
                    workstation,
                    order_batch,
                    material,
                    quality_mode
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
            if (records.isEmpty()) {
                continue;
            }

            for (ConsumerRecord<String, String> record : records) {
                try {
                    JSONObject json = new JSONObject(record.value());

                    long ts = json.optLong("timestamp", System.currentTimeMillis());
                    String machine = json.optString("machine", "unknown");
                    String eventType = json.optString("event", "UNKNOWN");
                    String cycleId = json.optString("cycleId", "N/A");
                    String phase = json.optString("phase", null);

                    Double spindleLoad = json.has("spindleLoad") ? json.optDouble("spindleLoad") : null;
                    Double surfaceFinish = json.has("surfaceFinish") ? json.optDouble("surfaceFinish") : null;
                    Double toolLife = json.has("toolLifeRemaining") ? json.optDouble("toolLifeRemaining") : null;
                    Double dimensionError = json.has("dimensionError") ? json.optDouble("dimensionError") : null;
                    Double progress = json.has("progress") ? json.optDouble("progress") : null;

                    JSONObject context = json.optJSONObject("context");
                    String plant = context != null ? context.optString("plant", null) : null;
                    String workstation = context != null ? context.optString("workstation", null) : null;
                    String orderBatch = context != null ? context.optString("order_batch", null) : null;
                    String material = context != null ? context.optString("material", null) : null;
                    String qualityMode = context != null ? context.optString("quality_mode", null) : null;


                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        ps.setTimestamp(1, new Timestamp(ts));
                        ps.setString(2, machine);
                        ps.setString(3, eventType);
                        ps.setString(4, cycleId);

                        if (phase != null) ps.setString(5, phase); else ps.setNull(5, Types.VARCHAR);
                        if (spindleLoad != null) ps.setDouble(6, spindleLoad); else ps.setNull(6, Types.DOUBLE);
                        if (surfaceFinish != null) ps.setDouble(7, surfaceFinish); else ps.setNull(7, Types.DOUBLE);
                        if (toolLife != null) ps.setDouble(8, toolLife); else ps.setNull(8, Types.DOUBLE);
                        if (dimensionError != null) ps.setDouble(9, dimensionError); else ps.setNull(9, Types.DOUBLE);
                        if (progress != null) ps.setDouble(10, progress); else ps.setNull(10, Types.DOUBLE);

                        if (plant != null) ps.setString(11, plant); else ps.setNull(11, Types.VARCHAR);
                        if (workstation != null) ps.setString(12, workstation); else ps.setNull(12, Types.VARCHAR);
                        if (orderBatch != null) ps.setString(13, orderBatch); else ps.setNull(13, Types.VARCHAR);
                        if (material != null) ps.setString(14, material); else ps.setNull(14, Types.VARCHAR);
                        if (qualityMode != null) ps.setString(15, qualityMode); else ps.setNull(15, Types.VARCHAR);

                        ps.executeUpdate();
                    }

                    System.out.printf("Inserted event: %s / %s / %s%n",
                            machine, eventType, cycleId);

                } catch (Exception e) {
                    System.err.println("Error processing record: " + e.getMessage());
                }
            }
        }
    }
}
