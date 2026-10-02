package com.sensormonitoring.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MeasurementTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rejectsMissingFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Measurement(null, "t1", SensorType.TEMPERATURE, 30, NOW))
                .withMessage("warehouseId");
        assertThatNullPointerException()
                .isThrownBy(() -> new Measurement("wh-1", "t1", SensorType.TEMPERATURE, 30, null))
                .withMessage("timestamp");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteValues(double value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Measurement("wh-1", "t1", SensorType.TEMPERATURE, value, NOW));
    }

    @Test
    void routingKeyCarriesTypeAndWarehouse() {
        assertThat(MeasurementTopology.routingKey(SensorType.HUMIDITY, "wh-2")).isEqualTo("measurement.humidity.wh-2");
    }
}
