package com.sensormonitoring.warehouse.ingestion;

import com.sensormonitoring.contract.Measurement;
import com.sensormonitoring.contract.SensorType;
import com.sensormonitoring.warehouse.config.WarehouseProperties;
import com.sensormonitoring.warehouse.config.WarehouseProperties.Channel;
import com.sensormonitoring.warehouse.publishing.MeasurementPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.Connection;
import reactor.netty.udp.UdpInbound;
import reactor.netty.udp.UdpServer;

@Component
class UdpSensorListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(UdpSensorListener.class);

    private static final String BIND_HOST = "0.0.0.0";
    private static final int RECEIVE_BUFFER_BYTES = 1 << 20;
    private static final int MAX_IN_FLIGHT = 32;
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final WarehouseProperties properties;
    private final MeasurementPublisher publisher;
    private final MeterRegistry meterRegistry;
    private final List<Binding> bindings = new CopyOnWriteArrayList<>();

    private volatile boolean running;

    UdpSensorListener(WarehouseProperties properties, MeasurementPublisher publisher, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void start() {
        properties.channels().forEach(channel -> bindings.add(bind(channel)));
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        bindings.forEach(binding -> binding.connection().dispose());
        Mono.when(bindings.stream().map(Binding::drained).toList())
                .timeout(SHUTDOWN_TIMEOUT)
                .onErrorResume(TimeoutException.class, e -> {
                    log.warn("Shutdown timed out before in-flight readings were published");
                    return Mono.empty();
                })
                .block();
        bindings.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private Binding bind(Channel channel) {
        IngestionMetrics metrics = IngestionMetrics.of(meterRegistry, channel.type());
        Sinks.Empty<Void> drained = Sinks.empty();
        Connection connection = UdpServer.create()
                .host(BIND_HOST)
                .port(channel.port())
                .option(ChannelOption.SO_RCVBUF, RECEIVE_BUFFER_BYTES)
                .handle((inbound, outbound) -> ingest(inbound, channel.type(), metrics)
                        .doFinally(signal -> drained.tryEmitEmpty()))
                .bindNow();
        log.info("Warehouse {} listening for {} readings on UDP {}",
                properties.id(), channel.type(), connection.channel().localAddress());
        return new Binding(connection, drained.asMono());
    }

    private Flux<Void> ingest(UdpInbound inbound, SensorType type, IngestionMetrics metrics) {
        return inbound.receiveObject()
                .ofType(DatagramPacket.class)
                .map(packet -> packet.content().toString(StandardCharsets.UTF_8))
                .flatMapIterable(payload -> payload.lines().filter(line -> !line.isBlank()).toList())
                .doOnNext(line -> metrics.received().increment())
                .<Measurement>handle((line, sink) -> MeasurementParser.parse(line).ifPresentOrElse(
                        reading -> sink.next(new Measurement(
                                properties.id(), reading.sensorId(), type, reading.value(), Instant.now())),
                        () -> {
                            metrics.invalid().increment();
                            log.warn("Discarded malformed {} reading '{}'", type, line);
                        }))
                .onBackpressureBuffer(properties.bufferCapacity(),
                        dropped -> metrics.dropped().increment(), BufferOverflowStrategy.DROP_OLDEST)
                .flatMap(measurement -> publisher.publish(measurement)
                        .onErrorResume(e -> {
                            metrics.publishFailed().increment();
                            log.warn("Failed to publish {} from sensor {}: {}",
                                    type, measurement.sensorId(), e.getMessage());
                            return Mono.empty();
                        }), MAX_IN_FLIGHT);
    }

    private record Binding(Connection connection, Mono<Void> drained) {
    }
}
