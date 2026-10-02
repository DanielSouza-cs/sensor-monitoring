package com.sensormonitoring.contract;

import java.util.Locale;

public final class MeasurementTopology {

    public static final String EXCHANGE = "sensor.measurements";
    public static final String ROUTING_KEY_PATTERN = "measurement.#";

    private MeasurementTopology() {
    }

    public static String routingKey(SensorType type, String warehouseId) {
        return "measurement." + type.name().toLowerCase(Locale.ROOT) + "." + warehouseId;
    }
}
