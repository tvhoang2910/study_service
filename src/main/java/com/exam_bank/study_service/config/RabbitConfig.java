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
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitConfig {

    public static final String DEFAULT_EXCHANGE = "exam.events";
    public static final String DEFAULT_ROUTING_KEY = "exam.submitted";
    public static final String DEFAULT_STUDY_QUEUE = "study.exam-submitted.queue";

    public static final String SR_EXCHANGE = "study.events";
    public static final String SR_QUEUE = "study.sr.queue";
    public static final String SR_ROUTING_KEY = "study.sr";

    @Bean
    public TopicExchange examEventsExchange(
            @Value("${exam.events.exchange:" + DEFAULT_EXCHANGE + "}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue examSubmittedQueue(
            @Value("${study.events.exam-submitted.queue:" + DEFAULT_STUDY_QUEUE + "}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding examSubmittedBinding(
            Queue examSubmittedQueue,
            TopicExchange examEventsExchange,
            @Value("${exam.events.routing-key:" + DEFAULT_ROUTING_KEY + "}") String routingKey) {
        return BindingBuilder.bind(examSubmittedQueue).to(examEventsExchange).with(routingKey);
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
