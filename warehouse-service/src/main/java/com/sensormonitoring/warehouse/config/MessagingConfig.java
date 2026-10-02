package com.sensormonitoring.warehouse.config;

import com.sensormonitoring.contract.MeasurementTopology;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class MessagingConfig {

    private static final int PUBLISHER_THREADS = 4;
    private static final int PUBLISHER_QUEUE_CAPACITY = 10_000;

    @Bean
    TopicExchange measurementsExchange() {
        return ExchangeBuilder.topicExchange(MeasurementTopology.EXCHANGE).durable(true).build();
    }

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean(destroyMethod = "dispose")
    Scheduler publisherScheduler() {
        return Schedulers.newBoundedElastic(PUBLISHER_THREADS, PUBLISHER_QUEUE_CAPACITY, "amqp-publisher");
    }
}
