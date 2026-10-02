package com.sensormonitoring.monitoring.alarm;

import com.sensormonitoring.contract.Measurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

@Component
class LoggingAlarmNotifier implements AlarmNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlarmNotifier.class);

    @Override
    public void notify(AlarmEvent event) {
        Measurement measurement = event.measurement();
        Level level = event.kind() == AlarmEvent.Kind.RAISED ? Level.WARN : Level.INFO;
        log.atLevel(level).log("ALARM {} warehouse={} sensor={} type={} value={} threshold={}",
                event.kind(), measurement.warehouseId(), measurement.sensorId(), measurement.type(),
                measurement.value(), event.threshold());
    }
}
