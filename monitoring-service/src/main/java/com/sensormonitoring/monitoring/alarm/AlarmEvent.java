package com.sensormonitoring.monitoring.alarm;

import com.sensormonitoring.contract.Measurement;

public record AlarmEvent(Kind kind, Measurement measurement, double threshold) {

    public enum Kind {
        RAISED,
        CLEARED
    }
}
