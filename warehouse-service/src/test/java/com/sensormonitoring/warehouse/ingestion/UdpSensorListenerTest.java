package com.sensormonitoring.warehouse.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sensormonitoring.contract.Measurement;
import com.sensormonitoring.contract.SensorType;
import com.sensormonitoring.warehouse.config.WarehouseProperties;
import com.sensormonitoring.warehouse.config.WarehouseProperties.Channel;
import com.sensormonitoring.warehouse.config.WarehouseProperties.Publish;
import com.sensormonitoring.warehouse.publishing.MeasurementPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UdpSensorListenerTest {

    private static final Duration PUBLISH_DELAY = Duration.ofMillis(500);

    private final int port = freePort();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final List<Measurement> published = new CopyOnWriteArrayList<>();
    private final MeasurementPublisher slowPublisher = measurement ->
            Mono.delay(PUBLISH_DELAY).doOnNext(tick -> published.add(measurement)).then();

    private final UdpSensorListener listener = new UdpSensorListener(
            new WarehouseProperties("wh-1", List.of(new Channel(SensorType.TEMPERATURE, port)), 100,
                    new Publish(Duration.ofSeconds(1), 0)),
            slowPublisher, meterRegistry);

    @AfterEach
    void stopListener() {
        if (listener.isRunning()) {
            listener.stop();
        }
    }

    @Test
    void stopWaitsForInFlightReadings() throws IOException {
        listener.start();
        send("sensor_id=t1; value=36");
        await().until(() -> meterRegistry.counter("sensor.measurements.received", "type", "temperature").count() == 1);

        listener.stop();

        assertThat(published).singleElement().satisfies(measurement -> {
            assertThat(measurement.warehouseId()).isEqualTo("wh-1");
            assertThat(measurement.sensorId()).isEqualTo("t1");
            assertThat(measurement.value()).isEqualTo(36);
        });
    }

    private void send(String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getLoopbackAddress(), port));
        }
    }

    private static int freePort() {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
