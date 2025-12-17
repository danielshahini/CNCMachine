package com.prosysopc.ua.samples.agent;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONObject;

import java.util.Random;
import java.util.concurrent.TimeUnit;


public class MqttOpcUa {

    private static final String DEFAULT_BROKER = "tcp://mqtt_broker:1883";
    private static final String TOPIC = "machines/cnc/state";

    private static final String MACHINE_NAME = "MyMachine";

    private final Random random = new Random();
    private long cycleCounter = 1;

    public static void main(String[] args) {
        try {
            String brokerUrl = System.getenv().getOrDefault("MQTT_BROKER", DEFAULT_BROKER);
            MqttOpcUa agent = new MqttOpcUa();
            agent.run(brokerUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void run(String brokerUrl) throws MqttException, InterruptedException {
        MqttClient client = new MqttClient(brokerUrl, MqttClient.generateClientId());
        client.connect();
        System.out.println("Connected to broker: " + brokerUrl);

        while (true) {
            String cycleId = "C-" + cycleCounter++;
            runSingleCycle(client, cycleId);
        }
    }

    private void runSingleCycle(MqttClient client, String cycleId) throws MqttException, InterruptedException {
        publishEvent(client, buildCycleStartEvent(cycleId));
        TimeUnit.SECONDS.sleep(5);

        publishEvent(client, buildPhaseChangeEvent(cycleId, "Setup", "Roughing"));
        TimeUnit.SECONDS.sleep(10);

        for (int step = 1; step <= 2; step++) {
            publishEvent(client, buildProgressEvent(cycleId, step * 20.0, "Roughing"));
            TimeUnit.SECONDS.sleep(10);
        }

        if (random.nextDouble() < 0.4) {
            publishEvent(client, buildToolWearEvent(cycleId));
            TimeUnit.SECONDS.sleep(8);
        }
        if (random.nextDouble() < 0.3) {
            publishEvent(client, buildDimensionDriftEvent(cycleId));
            TimeUnit.SECONDS.sleep(8);
        }

        publishEvent(client, buildPhaseChangeEvent(cycleId, "Roughing", "Finishing"));
        TimeUnit.SECONDS.sleep(10);

        for (int step = 3; step <= 4; step++) {
            publishEvent(client, buildProgressEvent(cycleId, step * 25.0, "Finishing"));
            TimeUnit.SECONDS.sleep(10);
        }

        publishEvent(client, buildCycleCompleteEvent(cycleId));
        TimeUnit.SECONDS.sleep(15);
    }

    private void publishEvent(MqttClient client, JSONObject event) throws MqttException {
        MqttMessage message = new MqttMessage(event.toString().getBytes());
        message.setQos(1);
        client.publish(TOPIC, message);
        System.out.println(event.getString("event") +
                " (cycle=" + event.optString("cycleId") + "): " + event);
    }

    private JSONObject baseEvent(String eventType, String cycleId) {
        JSONObject json = new JSONObject();
        json.put("machine", MACHINE_NAME);
        json.put("timestamp", System.currentTimeMillis());
        json.put("event", eventType);
        json.put("cycleId", cycleId);
        return json;
    }

    private JSONObject buildCycleStartEvent(String cycleId) {
        JSONObject json = baseEvent("CYCLE_START", cycleId);
        json.put("phase", "Setup");
        return json;
    }

    private JSONObject buildPhaseChangeEvent(String cycleId, String fromPhase, String toPhase) {
        JSONObject json = baseEvent("PHASE_CHANGE", cycleId);
        json.put("phase", toPhase);
        json.put("previousPhase", fromPhase);
        return json;
    }

    private JSONObject buildProgressEvent(String cycleId, double progress, String phase) {
        JSONObject json = baseEvent("CYCLE_PROGRESS", cycleId);
        json.put("progress", progress); // %
        json.put("phase", phase);
        double spindleLoad = 40.0 + random.nextDouble() * 50.0; // 40–90 %
        json.put("spindleLoad", spindleLoad);
        return json;
    }

    private JSONObject buildToolWearEvent(String cycleId) {
        JSONObject json = baseEvent("TOOL_WEAR", cycleId);
        double remaining = 5.0 + random.nextDouble() * 10.0; // 5–15 %
        json.put("toolLifeRemaining", remaining);
        return json;
    }

    private JSONObject buildDimensionDriftEvent(String cycleId) {
        JSONObject json = baseEvent("DIMENSION_DRIFT", cycleId);
        // ±0.02 mm Abweichung
        double error = (random.nextDouble() - 0.5) * 0.04;
        json.put("dimensionError", error);
        return json;
    }

    private JSONObject buildCycleCompleteEvent(String cycleId) {
        JSONObject json = baseEvent("CYCLE_COMPLETE", cycleId);
        double surface = 0.7 + (random.nextDouble() * 0.15);  // 0.7–0.85 µm
        double remaining = 60.0 + random.nextDouble() * 30.0; // 60–90 %
        json.put("surfaceFinish", surface);
        json.put("toolLifeRemaining", remaining);
        json.put("progress", 100.0);
        json.put("phase", "Inspection");
        return json;
    }
}
