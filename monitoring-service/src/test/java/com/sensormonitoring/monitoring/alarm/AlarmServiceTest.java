package com.sensormonitoring.monitoring.alarm;

import static org.assertj.core.api.Assertions.assertThat;

import com.sensormonitoring.contract.Measurement;
import com.sensormonitoring.contract.SensorType;
import com.sensormonitoring.monitoring.alarm.AlarmEvent.Kind;
import com.sensormonitoring.monitoring.config.MonitoringProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlarmServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final List<AlarmEvent> events = new ArrayList<>();
    private final AlarmService service = new AlarmService(
            new MonitoringProperties(Map.of(SensorType.TEMPERATURE, 35.0, SensorType.HUMIDITY, 50.0)),
            events::add,
            new SimpleMeterRegistry());

    @Test
    void raisesOnceWhileAboveThresholdAndClearsWhenBack() {
        service.evaluate(temperature("t1", 30, 0));
        service.evaluate(temperature("t1", 36, 1));
        service.evaluate(temperature("t1", 40, 2));
        service.evaluate(temperature("t1", 34, 3));

        assertThat(events).extracting(AlarmEvent::kind).containsExactly(Kind.RAISED, Kind.CLEARED);
        assertThat(events.getFirst().measurement().value()).isEqualTo(36);
        assertThat(events.getFirst().threshold()).isEqualTo(35);
    }

    @Test
    void valueEqualToThresholdIsNotAnAlarm() {
        service.evaluate(temperature("t1", 35, 0));

        assertThat(events).isEmpty();
    }

    @Test
    void tracksSensorsIndependently() {
        service.evaluate(temperature("t1", 36, 0));
        service.evaluate(temperature("t2", 36, 0));
        service.evaluate(new Measurement("wh-1", "h1", SensorType.HUMIDITY, 36, T0));

        assertThat(events).extracting(event -> event.measurement().sensorId()).containsExactly("t1", "t2");
    }

    @Test
    void redeliveredReadingIsIdempotent() {
        Measurement reading = temperature("t1", 36, 0);

        service.evaluate(reading);
        service.evaluate(reading);

        assertThat(events).extracting(AlarmEvent::kind).containsExactly(Kind.RAISED);
    }

    @Test
    void ignoresReadingsOlderThanTheLastObserved() {
        service.evaluate(temperature("t1", 36, 2));
        service.evaluate(temperature("t1", 30, 1));

        assertThat(events).extracting(AlarmEvent::kind).containsExactly(Kind.RAISED);
    }

    private static Measurement temperature(String sensorId, double value, long secondsAfterStart) {
        return new Measurement("wh-1", sensorId, SensorType.TEMPERATURE, value, T0.plusSeconds(secondsAfterStart));
    }
}
