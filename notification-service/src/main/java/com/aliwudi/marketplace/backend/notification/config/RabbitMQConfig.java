package com.aliwudi.marketplace.backend.notification.config;

import static com.aliwudi.marketplace.backend.common.constants.ExchangeType.*;
import static com.aliwudi.marketplace.backend.common.constants.EventRoutingKey.*;
import static com.aliwudi.marketplace.backend.common.constants.QueueType.*;
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

    // --- Queue Declarations ---
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

    // NEW: SMS Verification Queue
    @Bean
    public Queue smsVerificationQueue() {
        return new Queue(SMS_VERIFICATION_QUEUE, true); // Durable
    }

    // NEW: Phone Call Verification Queue
    @Bean
    public Queue phoneCallVerificationQueue() {
        return new Queue(PHONE_CALL_VERIFICATION_QUEUE, true); // Durable
    }

    // --- Binding Declarations ---
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

    // NEW: SMS Verification Binding
    @Bean
    public Binding smsVerificationBinding(Queue smsVerificationQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(smsVerificationQueue)
                             .to(userEventsExchange)
                             .with(SMS_VERIFICATION_ROUTING_KEY);
    }

    // NEW: Phone Call Verification Binding
    @Bean
    public Binding phoneCallVerificationBinding(Queue phoneCallVerificationQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(phoneCallVerificationQueue)
                             .to(userEventsExchange)
                             .with(PHONE_CALL_VERIFICATION_ROUTING_KEY);
    }

    // --- Message Converter ---
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