package com.sensormonitoring.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sensormonitoring.contract.Measurement;
import com.sensormonitoring.contract.MeasurementTopology;
import com.sensormonitoring.contract.SensorType;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "warehouse.id=wh-test",
        "warehouse.publish.max-retries=1",
        "warehouse.publish.confirm-timeout=2s"
})
class WarehouseIntegrationTest {

    private static final String TEST_QUEUE = "test.measurements";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int TEMPERATURE_PORT = freePort();
    private static final int HUMIDITY_PORT = freePort();

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @DynamicPropertySource
    static void channels(DynamicPropertyRegistry registry) {
        registry.add("warehouse.channels[0].type", () -> SensorType.TEMPERATURE);
        registry.add("warehouse.channels[0].port", () -> TEMPERATURE_PORT);
        registry.add("warehouse.channels[1].type", () -> SensorType.HUMIDITY);
        registry.add("warehouse.channels[1].port", () -> HUMIDITY_PORT);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private TopicExchange measurementsExchange;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JsonMapper jsonMapper;

    private final Queue testQueue = new Queue(TEST_QUEUE, false, false, false);

    @BeforeEach
    void declareTestQueue() {
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
        amqpAdmin.declareBinding(bindingFor(SensorType.TEMPERATURE));
    }

    @Test
    void publishesValidReadingsAndCountsInvalidOnes() throws IOException {
        send(TEMPERATURE_PORT, "garbage\nsensor_id=t1; value=36.5\n");

        Measurement measurement = receiveMeasurement();

        assertThat(measurement.warehouseId()).isEqualTo("wh-test");
        assertThat(measurement.sensorId()).isEqualTo("t1");
        assertThat(measurement.type()).isEqualTo(SensorType.TEMPERATURE);
        assertThat(measurement.value()).isEqualTo(36.5);
        assertThat(counter("sensor.measurements.invalid", "temperature")).isEqualTo(1);
    }

    @Test
    void unroutableReadingFailsWithoutStoppingTheChannel(CapturedOutput output) throws IOException {
        amqpAdmin.removeBinding(bindingFor(SensorType.HUMIDITY));
        send(HUMIDITY_PORT, "sensor_id=h1; value=55");

        await().atMost(TIMEOUT)
                .until(() -> counter("sensor.measurements.publish.failed", "humidity") >= 1);
        assertThat(output).contains("Failed to publish HUMIDITY from sensor h1: unroutable");

        amqpAdmin.declareBinding(bindingFor(SensorType.HUMIDITY));
        send(HUMIDITY_PORT, "sensor_id=h2; value=45");

        Measurement measurement = receiveMeasurement();
        assertThat(measurement.sensorId()).isEqualTo("h2");
    }

    private Measurement receiveMeasurement() {
        Message message = rabbitTemplate.receive(TEST_QUEUE, TIMEOUT.toMillis());
        assertThat(message).isNotNull();
        return jsonMapper.readValue(message.getBody(), Measurement.class);
    }

    private Binding bindingFor(SensorType type) {
        String routingKey = MeasurementTopology.routingKey(type, "#");
        return BindingBuilder.bind(testQueue).to(measurementsExchange).with(routingKey);
    }

    private double counter(String name, String type) {
        return meterRegistry.counter(name, "type", type).count();
    }

    private static void send(int port, String payload) throws IOException {
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
