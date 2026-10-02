package com.sensormonitoring.warehouse.publishing;

import com.sensormonitoring.contract.Measurement;
import com.sensormonitoring.contract.MeasurementTopology;
import com.sensormonitoring.warehouse.config.WarehouseProperties;
import java.time.Duration;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.retry.Retry;

@Component
class RabbitMeasurementPublisher implements MeasurementPublisher {

    private static final Duration MIN_BACKOFF = Duration.ofMillis(100);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(2);

    private final RabbitTemplate rabbitTemplate;
    private final Scheduler publisherScheduler;
    private final Duration confirmTimeout;
    private final int maxRetries;

    RabbitMeasurementPublisher(RabbitTemplate rabbitTemplate, Scheduler publisherScheduler,
                               WarehouseProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.publisherScheduler = publisherScheduler;
        this.confirmTimeout = properties.publish().confirmTimeout();
        this.maxRetries = properties.publish().maxRetries();
    }

    @Override
    public Mono<Void> publish(Measurement measurement) {
        return Mono.defer(() -> send(measurement))
                .retryWhen(Retry.backoff(maxRetries, MIN_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    private Mono<Void> send(Measurement measurement) {
        String routingKey = MeasurementTopology.routingKey(measurement.type(), measurement.warehouseId());
        CorrelationData correlation = new CorrelationData();
        return Mono.fromRunnable(() -> rabbitTemplate.convertAndSend(
                        MeasurementTopology.EXCHANGE, routingKey, measurement, correlation))
                .subscribeOn(publisherScheduler)
                .then(Mono.fromFuture(correlation.getFuture(), true))
                .timeout(confirmTimeout)
                .flatMap(confirm -> {
                    if (!confirm.ack()) {
                        return Mono.error(new PublishException("nacked by broker: " + confirm.reason()));
                    }
                    if (correlation.getReturned() != null) {
                        return Mono.error(new PublishException("unroutable: " + routingKey));
                    }
                    return Mono.empty();
                });
    }
}
