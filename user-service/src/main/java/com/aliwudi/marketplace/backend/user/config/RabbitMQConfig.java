package com.aliwudi.marketplace.backend.user.config; 

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Define the exchange name. This must match the exchange defined in notification-service.
    public static final String USER_EVENTS_EXCHANGE = "user.events.exchange";

    @Bean
    public TopicExchange userEventsExchange() {
        // Durable exchange, not auto-delete.
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
    }

    // Configures a message converter to serialize/deserialize messages as JSON
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Configures RabbitTemplate to use the JSON message converter for publishing
    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}