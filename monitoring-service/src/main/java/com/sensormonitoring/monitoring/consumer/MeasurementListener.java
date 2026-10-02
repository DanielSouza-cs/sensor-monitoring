package com.sensormonitoring.monitoring.consumer;

import com.sensormonitoring.contract.Measurement;
import com.sensormonitoring.monitoring.alarm.AlarmService;
import com.sensormonitoring.monitoring.config.MessagingConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class MeasurementListener {

    private final AlarmService alarmService;

    MeasurementListener(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @RabbitListener(queues = MessagingConfig.MEASUREMENTS_QUEUE)
    void onMeasurement(Measurement measurement) {
        alarmService.evaluate(measurement);
    }
}
