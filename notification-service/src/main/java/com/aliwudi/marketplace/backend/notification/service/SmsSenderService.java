package com.aliwudi.marketplace.backend.notification.service;

import reactor.core.publisher.Mono;

/**
 * Interface for sending SMS messages.
 */
public interface SmsSenderService {
    Mono<Void> sendSms(String toPhoneNumber, String messageBody);
}