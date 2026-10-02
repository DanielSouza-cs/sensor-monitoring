package com.sensormonitoring.monitoring.config;

import com.sensormonitoring.contract.MeasurementTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class MessagingConfig {

    public static final String MEASUREMENTS_QUEUE = "monitoring.measurements";
    public static final String DEAD_LETTER_EXCHANGE = "monitoring.measurements.dlx";
    public static final String DEAD_LETTER_QUEUE = "monitoring.measurements.dlq";

    @Bean
    TopicExchange measurementsExchange() {
        return ExchangeBuilder.topicExchange(MeasurementTopology.EXCHANGE).durable(true).build();
    }

    @Bean
    FanoutExchange deadLetterExchange() {
        return ExchangeBuilder.fanoutExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue measurementsQueue() {
        return QueueBuilder.durable(MEASUREMENTS_QUEUE)
                .quorum()
                .singleActiveConsumer()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).quorum().build();
    }

    @Bean
    Binding measurementsBinding(Queue measurementsQueue, TopicExchange measurementsExchange) {
        return BindingBuilder.bind(measurementsQueue).to(measurementsExchange)
                .with(MeasurementTopology.ROUTING_KEY_PATTERN);
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
