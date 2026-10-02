package com.sensormonitoring.monitoring.alarm;

public interface AlarmNotifier {

    void notify(AlarmEvent event);
}
