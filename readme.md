# CNC Event Processing Pipeline

## Overview

This project implements an **event-driven CNC data pipeline** based on OPC UA, MQTT, Kafka (Redpanda), TimescaleDB and Grafana.
It simulates CNC machine behavior, enriches events with production context and provides both real-time monitoring and long-term analytics.

---

## Prerequisites

Make sure the following tools are installed:

* **Java 21**
* **Maven 3.9+**
* **Docker & Docker Compose (v2)**
* (Recommended) IntelliJ IDEA

---

## Build & Run Instructions

### 1. Build the Java Components

All Java components (OPC UA server and agents) are built using Maven. This creates **fat JARs** with all dependencies included.

```bash
mvn clean package
```

Alternatively (IntelliJ IDEA):

* Open the **Maven** panel
* Run **Lifecycle → clean**
* Then **Lifecycle → package**

After a successful build, the JAR files are available in:

```
target/
```

---

### 2. Start the Infrastructure with Docker Compose

Build the Docker images:

```bash
docker compose build
```

Start all services in detached mode:

```bash
docker compose up -d
```

To stop everything:

```bash
docker compose down
```

---

## Service Access & Credentials

### Grafana (Dashboards)

* URL: [http://localhost:3000](http://localhost:3000)
* User: `daniel`
* Password: `daniel`

Used for visualizing CNC events, machine utilization and tool wear.

---

### TimescaleDB (PostgreSQL)

* Host: `localhost`
* Port: `5432`
* Database: `mydb`
* User: `daniel`
* Password: `daniel`

Used as the persistent time-series database.

---

### pgAdmin (Database UI)

* URL: [http://localhost:5050](http://localhost:5050)
* Email: `daniel@local.com`
* Password: `daniel`

To connect TimescaleDB inside pgAdmin:

* Host: `timescaledb`
* Port: `5432`
* User: `daniel`
* Password: `daniel`
* Database: `mydb`

---

### MQTT Broker (Mosquitto)

* Host: `localhost`
* Port: `1883`
* Authentication: **disabled** (anonymous access enabled)

Used for publishing CNC events from the OPC UA agent.

---

### Redis & RedisInsight

**Redis:**

* Host: `localhost`
* Port: `6379`
* Authentication: none

**RedisInsight UI:**

* URL: [http://localhost:8001](http://localhost:8001)

Redis is used for **context enrichment** (plant, workstation, order, material, quality mode).

---

### Redpanda (Kafka-compatible Broker)

* Kafka External Port: `19092`
* Internal Broker: `redpanda_broker:9092`

Used as the central event streaming backbone.

---

### Redpanda Console

* URL: [http://localhost:8087](http://localhost:8087)

Provides insight into:

* Kafka topics
* Messages
* Consumer groups
* Broker health

---

## OPC UA Simulation Server

* Endpoint Port: `52520`

The OPC UA server simulates a CNC machine and provides:

* Cycle-based events
* Spindle load
* Tool wear
* Quality metrics

The server is automatically started inside Docker.

---

## Agents Overview

| Agent     | Responsibility                              |
| --------- | ------------------------------------------- |
| MqttOpcUa | Reads OPC UA data and publishes MQTT events |
| Hydration | Enriches events with Redis context          |
| Timescale | Writes enriched events into TimescaleDB     |

All agents are started automatically via Docker Compose.

---

## Network Setup

All services run in the Docker network:

```
advanced_data_management
```

If the network does not exist yet, create it once:

```bash
docker network create advanced_data_management
```

---

## Troubleshooting

**Problem:** `ClassNotFoundException`

* Make sure Maven build completed successfully
* Verify class names match `mainClass` entries
* Rebuild images without cache:

```bash
docker compose build --no-cache
```

---

## Notes

* All credentials are **development-only** and intentionally simple
* This setup is not hardened for production
* Designed for educational and demonstration purposes

---

## Author

CNC Event Processing Pipeline – Advanced Data Management Project
