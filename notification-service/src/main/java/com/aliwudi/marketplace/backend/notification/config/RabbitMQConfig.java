package com.aliwudi.marketplace.backend.notification.config;

import static com.aliwudi.marketplace.backend.common.constants.EventType.*;
import static com.aliwudi.marketplace.backend.common.constants.EventRoutingKey.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {



    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false); // Durable, not auto-delete
    }

    // --- Queue Definitions ---
    public static final String EMAIL_VERIFICATION_QUEUE = "email.verification.queue";
    public static final String REGISTRATION_ONBOARDING_QUEUE = "registration.onboarding.queue";
    public static final String PASSWORD_RESET_QUEUE = "password.reset.queue";

    @Bean
    public Queue emailVerificationQueue() {
        return new Queue(EMAIL_VERIFICATION_QUEUE, true); // Durable
    }

    @Bean
    public Queue registrationOnboardingQueue() {
        return new Queue(REGISTRATION_ONBOARDING_QUEUE, true); // Durable
    }

    @Bean
    public Queue passwordResetQueue() {
        return new Queue(PASSWORD_RESET_QUEUE, true); // Durable
    }


    @Bean
    public Binding emailVerificationBinding(Queue emailVerificationQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(emailVerificationQueue)
                             .to(userEventsExchange)
                             .with(EMAIL_VERIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding registrationOnboardingBinding(Queue registrationOnboardingQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(registrationOnboardingQueue)
                             .to(userEventsExchange)
                             .with(USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public Binding passwordResetBinding(Queue passwordResetQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(passwordResetQueue)
                             .to(userEventsExchange)
                             .with(PASSWORD_RESET_ROUTING_KEY);
    }

    // --- Message Converter ---
    // This is crucial for sending/receiving Java objects as JSON messages
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Configures RabbitTemplate to use the JSON message converter
    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}