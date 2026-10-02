# Sensor Monitoring

Warehouses receive temperatuire and humidity readings from sensors over UDP and publish them to a central
monitoring service, which logs an alarm when a reading goes above its threshold.

```
sensors ──UDP──▶ warehouse-service ──RabbitMQ──▶ monitoring-service ──▶ ALARM in the console
```

| Sensor      | UDP port | Payload                  | Threshold |
|-------------|----------|--------------------------|-----------|
| Temperature | 3344     | `sensor_id=t1; value=30` | 35 °C     |
| Humidity    | 3355     | `sensor_id=h1; value=40` | 50 %      |

## Running

Requirements: Java 21 and Docker.

```bash
make up         # RabbitMQ + monitoring + two warehouses
make simulate   # sends a few readings to wh-1 with netcat
make logs
make down
```

A single reading by hand:

```bash
echo "sensor_id=t1; value=38" | nc -u -w1 localhost 3344
```

Expected output in the monitoring service:

```
WARN  ALARM RAISED warehouse=wh-1 sensor=t1 type=TEMPERATURE value=38.0 threshold=35.0
INFO  ALARM CLEARED warehouse=wh-1 sensor=t1 type=TEMPERATURE value=33.0 threshold=35.0
```

`make test` runs all tests (integration tests start RabbitMQ with Testcontainers). Coverage reports are written to
`<module>/target/site/jacoco`.

## How it works

**Modules**

- `measurement-contract`: the `Measurement` record and the exchange/routing key shared by both services.
- `warehouse-service`: UDP ingestion and publishing.
- `monitoring-service`: consumption and alarm evaluation.

**Warehouse**

- One Reactor Netty UDP server per port; the port defines the sensor type. The pipeline is non-blocking end to end.
- Each line is parsed against the exact format. Malformed lines are logged and discarded.
- Readings go through a bounded buffer (drops the oldest when full) and are published with at most 32 in flight.
- Publishing waits for the broker confirm. Nacks, unroutable messages and confirm timeouts are retried with
  exponential backoff. A failed reading is logged and never stops the pipeline.
- On shutdown the UDP ports are closed first and in-flight readings are drained before the connection closes.

**Broker (RabbitMQ)**

- Topic exchange `sensor.measurements`, routing key `measurement.<type>.<warehouseId>`.
- Durable quorum queue `monitoring.measurements` with a single active consumer, so readings are evaluated in order.
- Messages are acknowledged only after processing. Messages that cannot be read go to `monitoring.measurements.dlq`.

**Monitoring**

- Alarm rule: `value > threshold` (equal is not an alarm). Thresholds come from `application.yml`.
- State is kept per `(warehouse, type, sensor)`, and only changes are logged: `RAISED` when a reading crosses the
  threshold and `CLEARED` when it goes back. This avoids repeating the alarm for every reading.
- Delivery is at-least-once, so evaluation is idempotent: a duplicate does not change the state, and a reading
  older than the last one seen for that sensor is ignored.

## Configuration

| Property                            | Default                         |
|-------------------------------------|---------------------------------|
| `warehouse.id` (`WAREHOUSE_ID`)     | `wh-1`                          |
| `warehouse.channels`                | TEMPERATURE 3344, HUMIDITY 3355 |
| `warehouse.buffer-capacity`         | 10000                           |
| `warehouse.publish.confirm-timeout` | 5s                              |
| `warehouse.publish.max-retries`     | 5                               |
| `monitoring.thresholds.TEMPERATURE` | 35                              |
| `monitoring.thresholds.HUMIDITY`    | 50                              |

## Stack

Java 21, Spring Boot 4, Reactor Netty, Spring AMQP, RabbitMQ 4, JUnit, AssertJ, Awaitility, Testcontainers.
