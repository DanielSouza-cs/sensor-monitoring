package com.sensormonitoring.monitoring.alarm;

import com.sensormonitoring.contract.Measurement;
import com.sensormonitoring.contract.SensorType;
import com.sensormonitoring.monitoring.alarm.AlarmEvent.Kind;
import com.sensormonitoring.monitoring.config.MonitoringProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    private final Map<SensorKey, SensorState> states = new ConcurrentHashMap<>();
    private final MonitoringProperties properties;
    private final AlarmNotifier notifier;
    private final MeterRegistry meterRegistry;

    public AlarmService(MonitoringProperties properties, AlarmNotifier notifier, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.notifier = notifier;
        this.meterRegistry = meterRegistry;
        log.info("Alarm thresholds {}", properties.thresholds());
    }

    public void evaluate(Measurement measurement) {
        double threshold = properties.thresholdFor(measurement.type());
        boolean exceeded = measurement.value() > threshold;
        SensorState state = states.compute(SensorKey.of(measurement),
                (key, current) -> SensorState.next(current, measurement.timestamp(), exceeded));
        if (state.changed()) {
            Kind kind = state.alarming() ? Kind.RAISED : Kind.CLEARED;
            meterRegistry.counter("sensor.alarms",
                    "type", measurement.type().name().toLowerCase(Locale.ROOT),
                    "state", kind.name().toLowerCase(Locale.ROOT)).increment();
            notifier.notify(new AlarmEvent(kind, measurement, threshold));
        }
    }

    private record SensorKey(String warehouseId, SensorType type, String sensorId) {

        static SensorKey of(Measurement measurement) {
            return new SensorKey(measurement.warehouseId(), measurement.type(), measurement.sensorId());
        }
    }

    private record SensorState(Instant observedAt, boolean alarming, boolean changed) {

        static SensorState next(SensorState current, Instant observedAt, boolean exceeded) {
            if (current == null) {
                return new SensorState(observedAt, exceeded, exceeded);
            }
            if (observedAt.isBefore(current.observedAt())) {
                return new SensorState(current.observedAt(), current.alarming(), false);
            }
            return new SensorState(observedAt, exceeded, exceeded != current.alarming());
        }
    }
}
