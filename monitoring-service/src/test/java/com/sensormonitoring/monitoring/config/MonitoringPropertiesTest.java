package com.sensormonitoring.monitoring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sensormonitoring.contract.SensorType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonitoringPropertiesTest {

    @Test
    void failsFastWhenASensorTypeHasNoThreshold() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MonitoringProperties(Map.of(SensorType.TEMPERATURE, 35.0)))
                .withMessageContaining("HUMIDITY");
    }

    @Test
    void resolvesThresholdPerType() {
        MonitoringProperties properties = new MonitoringProperties(
                Map.of(SensorType.TEMPERATURE, 35.0, SensorType.HUMIDITY, 50.0));

        assertThat(properties.thresholdFor(SensorType.TEMPERATURE)).isEqualTo(35);
        assertThat(properties.thresholdFor(SensorType.HUMIDITY)).isEqualTo(50);
    }
}
