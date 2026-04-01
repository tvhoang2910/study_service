package com.exam_bank.study_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "exam.events";
    public static final String QUEUE = "exam.submitted.queue";
    public static final String ROUTING_KEY = "exam.submitted";

    public static final String SR_EXCHANGE = "study.events";
    public static final String SR_QUEUE = "study.sr.queue";
    public static final String SR_ROUTING_KEY = "study.sr";

    @Bean
    public TopicExchange examEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue examSubmittedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding examSubmittedBinding(Queue examSubmittedQueue, TopicExchange examEventsExchange) {
        return BindingBuilder.bind(examSubmittedQueue).to(examEventsExchange).with(ROUTING_KEY);
    }

    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
