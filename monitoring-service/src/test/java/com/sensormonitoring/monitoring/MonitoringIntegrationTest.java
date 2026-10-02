package com.sensormonitoring.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sensormonitoring.contract.Measurement;
import com.sensormonitoring.contract.MeasurementTopology;
import com.sensormonitoring.contract.SensorType;
import com.sensormonitoring.monitoring.config.MessagingConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MonitoringIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void logsAlarmWhenReadingCrossesThresholdAndWhenItClears(CapturedOutput output) {
        Instant now = Instant.now();
        Measurement hot = new Measurement("wh-1", "t1", SensorType.TEMPERATURE, 38, now);
        publish(hot);
        publish(hot);
        publish(new Measurement("wh-1", "t1", SensorType.TEMPERATURE, 30, now.plusSeconds(1)));

        String raised = "ALARM RAISED warehouse=wh-1 sensor=t1 type=TEMPERATURE value=38.0 threshold=35.0";
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(output)
                .contains(raised)
                .contains("ALARM CLEARED warehouse=wh-1 sensor=t1 type=TEMPERATURE value=30.0 threshold=35.0"));
        assertThat(output.getOut().split(raised, -1)).as("duplicate delivery raises once").hasSize(2);
    }

    @Test
    void deadLettersMalformedMessages() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message poison = MessageBuilder.withBody("{\"sensorId\":\"t9\"}".getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();

        rabbitTemplate.send(MeasurementTopology.EXCHANGE, "measurement.temperature.wh-1", poison);

        Message deadLettered = rabbitTemplate.receive(MessagingConfig.DEAD_LETTER_QUEUE, TIMEOUT.toMillis());
        assertThat(deadLettered).isNotNull();
        assertThat(new String(deadLettered.getBody(), StandardCharsets.UTF_8)).contains("t9");
    }

    private void publish(Measurement measurement) {
        rabbitTemplate.convertAndSend(MeasurementTopology.EXCHANGE,
                MeasurementTopology.routingKey(measurement.type(), measurement.warehouseId()), measurement);
    }
}
