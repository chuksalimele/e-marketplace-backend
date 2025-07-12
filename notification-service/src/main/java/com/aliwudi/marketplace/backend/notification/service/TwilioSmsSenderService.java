package com.aliwudi.marketplace.backend.notification.service;

import com.aliwudi.marketplace.backend.common.exception.NotificationException;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct; // For @PostConstruct if needed

/**
 * Twilio implementation of SmsSenderService.
 */
@Service
@Slf4j
public class TwilioSmsSenderService implements SmsSenderService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    @PostConstruct // Initialize Twilio SDK when service is created
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    @Override
    public Mono<Void> sendSms(String toPhoneNumber, String messageBody) {
        return Mono.fromCallable(() -> {
            try {
                Message message = Message.creator(
                                new PhoneNumber(toPhoneNumber), // to
                                new PhoneNumber(twilioPhoneNumber), // from
                                messageBody)
                        .create();
                log.info("SMS sent successfully to {} with SID: {}", toPhoneNumber, message.getSid());
                return null; // For Mono<Void> completion
            } catch (Exception e) {
                log.error("Failed to send SMS to {}: {}", toPhoneNumber, e.getMessage(), e);
                throw new NotificationException("Failed to send SMS to " + toPhoneNumber, e);
            }
        })
        .onErrorResume(NotificationException.class, Mono::error)
        .onErrorResume(e -> {
            log.error("An unexpected error occurred during SMS sending for {}: {}", toPhoneNumber, e.getMessage(), e);
            return Mono.error(new NotificationException("An unexpected error occurred during SMS sending.", e));
        })
        .then()
        .subscribeOn(Schedulers.boundedElastic()); // Run blocking Twilio call on a separate thread
    }
}