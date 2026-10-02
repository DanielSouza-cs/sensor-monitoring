package com.sensormonitoring.contract;

import java.time.Instant;
import java.util.Objects;

public record Measurement(String warehouseId, String sensorId, SensorType type, double value, Instant timestamp) {

    public Measurement {
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(sensorId, "sensorId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timestamp, "timestamp");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
    }
}
