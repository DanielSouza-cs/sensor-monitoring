package com.sensormonitoring.warehouse.ingestion;

import com.sensormonitoring.contract.SensorType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;

record IngestionMetrics(Counter received, Counter invalid, Counter dropped, Counter publishFailed) {

    static IngestionMetrics of(MeterRegistry registry, SensorType type) {
        String tag = type.name().toLowerCase(Locale.ROOT);
        return new IngestionMetrics(
                registry.counter("sensor.measurements.received", "type", tag),
                registry.counter("sensor.measurements.invalid", "type", tag),
                registry.counter("sensor.measurements.dropped", "type", tag),
                registry.counter("sensor.measurements.publish.failed", "type", tag));
    }
}
