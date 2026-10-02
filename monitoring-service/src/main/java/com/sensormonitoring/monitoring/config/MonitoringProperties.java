package com.sensormonitoring.monitoring.config;

import com.sensormonitoring.contract.SensorType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("monitoring")
public record MonitoringProperties(Map<SensorType, Double> thresholds) {

    public MonitoringProperties {
        if (thresholds == null || !thresholds.keySet().containsAll(EnumSet.allOf(SensorType.class))) {
            throw new IllegalArgumentException("monitoring.thresholds must define every sensor type "
                    + EnumSet.allOf(SensorType.class));
        }
        thresholds = Collections.unmodifiableMap(new EnumMap<>(thresholds));
    }

    public double thresholdFor(SensorType type) {
        return thresholds.get(type);
    }
}
