package com.sensormonitoring.warehouse.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.sensormonitoring.warehouse.ingestion.MeasurementParser.Reading;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MeasurementParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "sensor_id=t1; value=30            | t1      | 30",
            "sensor_id=h1;value=40.5           | h1      | 40.5",
            "'  sensor_id = t-2 ; value = -3  '| t-2     | -3",
            "sensor_id=t1.a_b; value=+7        | t1.a_b  | 7"
    })
    void parsesValidPayloads(String payload, String sensorId, double value) {
        assertThat(MeasurementParser.parse(payload)).contains(new Reading(sensorId, value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "garbage",
            "sensor_id=t1",
            "value=30",
            "value=30; sensor_id=t1",
            "sensor_id=; value=30",
            "sensor_id=t 1; value=30",
            "sensor_id=t1; value=",
            "sensor_id=t1; value=abc",
            "sensor_id=t1; value=NaN",
            "sensor_id=t1; value=Infinity",
            "sensor_id=t1; value=30d",
            "sensor_id=t1; value=1e3",
            "sensor_id=t1; value=0x1p5",
            "sensor_id=t1; value=1234567890",
            "sensor_id=t1; value=30; unit=C",
            "sensor_id=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa; value=30"
    })
    void rejectsInvalidPayloads(String payload) {
        assertThat(MeasurementParser.parse(payload)).isEmpty();
    }
}
