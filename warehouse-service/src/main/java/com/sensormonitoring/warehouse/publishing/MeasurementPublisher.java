package com.sensormonitoring.warehouse.publishing;

import com.sensormonitoring.contract.Measurement;
import reactor.core.publisher.Mono;

public interface MeasurementPublisher {

    Mono<Void> publish(Measurement measurement);
}
