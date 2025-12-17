# 📄 PROJECT DOCUMENTATION

## 1. Überblick

Dieses Dokument beschreibt den Aufbau, die Architektur und die Datenflüsse des Projekts **CNC Machining Center**. Ziel des Systems ist die Simulation, Erfassung, Anreicherung, Speicherung und Visualisierung von CNC-Maschinendaten in nahezu Echtzeit sowie für langfristige Analysen.

Der Fokus liegt auf:

* industrieller Datenintegration (OPC UA, MQTT, Kafka)
* ereignisbasierter Datenverarbeitung
* Zeitreihenanalyse mit TimescaleDB
* Visualisierung mit Grafana

---

## 2. OPC-UA Server Overview

Der OPC-UA Server simuliert eine CNC-Maschine und erzeugt realitätsnahe Produktions- und Sensordaten. Die Simulation basiert auf:

* **Cycle-basierten Ereignissen** (Start, Fortschritt, Ende)
* **Qualitätsmessungen**
* **Werkzeugverschleiß**
* **Dimensionsabweichungen**

### 2.1 Verfügbare Felder

| Node                            | Bedeutung                 |
| ------------------------------- | ------------------------- |
| Machine/MachineStatus           | Running / Stopped / Error |
| Machine/ActualSpindleSpeed      | Drehzahl in U/min         |
| Machine/CoolantTemperature      | Temperatur in °C          |
| Machine/ToolLifeRemaining       | Werkzeugverschleiß (%)    |
| Machine/ProductionOrderProgress | Fortschritt               |
| Machine/Cycle/ActiveCycleId     | ID des laufenden Zyklus   |
| Machine/Cycle/CurrentPhase      | Roughing, Finishing etc.  |

Diese Werte ändern sich zyklisch im Simulationsintervall, um realistische Produktionsabläufe nachzubilden.

---

## 3. Systemarchitektur

### 3.1 Architekturübersicht

```
[OPC-UA Server]
        ↓
 MqttOpcUaAgent
        ↓
   MQTT Broker
        ↓
  HydrationAgent
        ↓
       Kafka
        ↓
  TimescaleAgent
        ↓
   TimescaleDB
        ↓
      Grafana
```

### 3.2 Komponentenbeschreibung

* **MqttOpcUaAgent**
  Liest Daten vom OPC-UA Server und veröffentlicht sie als MQTT-Nachrichten.

* **MQTT Broker**
  Zentrale Messaging-Schicht für lose Kopplung der Komponenten.

* **HydrationAgent**
  Reichert Rohdaten mit Kontextinformationen (z. B. Werk, Auftrag, Material) an und veröffentlicht diese in Kafka.

* **Kafka (Redpanda)**
  Dient als skalierbare Event-Streaming-Plattform.

* **TimescaleAgent**
  Konsumiert Kafka-Nachrichten und persistiert sie strukturiert in TimescaleDB.

* **TimescaleDB**
  Zeitreihenoptimierte PostgreSQL-Datenbank zur langfristigen Speicherung.

* **Grafana**
  Visualisiert Live- und historische Daten.

---

### 3.3 Benutzeroberflächen

| Anwendung        | URL                                            |
| ---------------- | ---------------------------------------------- |
| Grafana          | [http://localhost:3000](http://localhost:3000) |
| Redpanda Console | [http://localhost:8087](http://localhost:8087) |

### 3.4 Portübersicht

| Komponente       | Port  |
| ---------------- |-------|
| OPC-UA           | 52520 |
| MQTT             | 1883  |
| Kafka (extern)   | 18081 |
| Redpanda Console | 8087  |
| TimescaleDB      | 5432  |
| Grafana          | 3000  |

---

## 4. Datenbankschema (cnc_events)

Die Tabelle **cnc_events** speichert ereignisbasierte Produktions- und Sensordaten.

### 4.1 Tabellenstruktur

```sql
CREATE TABLE cnc_events (
    time TIMESTAMPTZ NOT NULL,
    machine_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    cycle_id TEXT NOT NULL,
    phase TEXT,
    spindle_load DOUBLE PRECISION,
    surface_finish DOUBLE PRECISION,
    tool_life_remaining DOUBLE PRECISION,
    dimension_error DOUBLE PRECISION,
    progress_percent DOUBLE PRECISION,

    -- Enrichment-Felder
    plant TEXT,
    workstation TEXT,
    order_batch TEXT,
    material TEXT,
    quality_mode TEXT,

    PRIMARY KEY (time, machine_id, event_type, cycle_id)
);
```

### 4.2 Feldbeschreibung

| Feld            | Beschreibung                             |
| --------------- | ---------------------------------------- |
| event_type      | CYCLE_START / CYCLE_PROGRESS / CYCLE_END |
| cycle_id        | Produktionsauftrag / Zyklusnummer        |
| spindle_load    | Spindelbelastung in Prozent              |
| surface_finish  | Oberflächenrauheit (RMS)                 |
| dimension_error | Maßabweichung                            |
| order_batch     | Fertigungslos                            |
| quality_mode    | Qualitätsstandard (z. B. Aerospace)      |

---

# Grafana Dashboard Documentation

## Overview

This dashboard visualizes CNC machine event and sensor data stored in a TimescaleDB database.
It is designed to provide both **short-term monitoring** of current system behavior and **long-term analysis** of historical trends.

The dashboard consists of **four panels**:

* Two short-term panels for recent and live data
* Two long-term panels for aggregated, historical trend analysis

This structure fulfills the requirement to monitor current system behavior and identify long-term patterns.

---

## Panel 1: Event Activity (Short-Term)

### SQL Query

```sql
SELECT
  time_bucket('5 minutes', time) AS time,
  COUNT(*) AS events
FROM cnc_events
WHERE $__timeFilter(time)
GROUP BY time
ORDER BY time
```

### Interpretation

This panel shows the number of events per 5-minute interval within the selected time range.
It provides insight into the current activity level of the CNC system.

Higher values indicate intensive system activity, while lower values suggest idle or low-activity periods.

### Purpose

Used for short-term monitoring to quickly detect spikes or drops in system activity.

---

## Panel 2: Spindle Load (Short-Term)

### SQL Query

```sql
SELECT
  time,
  spindle_load,
  machine_id
FROM cnc_events
WHERE
  $__timeFilter(time)
  AND spindle_load IS NOT NULL
ORDER BY time
```

### Interpretation

This panel visualizes spindle load over time for each machine.
Each line represents one machine and shows how heavily it is loaded during operation.

Fluctuations indicate changes in machining phases or workload.

### Purpose

Supports short-term operational monitoring and helps identify overload or abnormal behavior.

---

## Panel 3: Average Tool Life Remaining (Long-Term)

### SQL Query

```sql
SELECT
  time_bucket('1 day', time) AS time,
  AVG(tool_life_remaining) AS avg_tool_life
FROM cnc_events
WHERE tool_life_remaining IS NOT NULL
GROUP BY time
ORDER BY time
```

### Interpretation

This panel displays the average remaining tool life per day aggregated across all machines.
A gradual downward trend indicates normal tool wear, while sudden drops may indicate issues.

### Purpose

Used for long-term trend analysis and predictive maintenance planning.

---

## Panel 4: Machine Utilization Trend (Daily)

### SQL Query

```sql
SELECT
  time_bucket('1 day', time) AS time,
  machine_id,
  COUNT(*) AS events_per_day
FROM cnc_events
GROUP BY time, machine_id
ORDER BY time
```

### Interpretation

This panel shows the number of events per machine per day and serves as an indicator of machine utilization.
Higher values indicate higher utilization, while lower values suggest reduced usage or downtime.

### Purpose

Provides a long-term view of machine usage patterns and supports capacity planning and optimization.
