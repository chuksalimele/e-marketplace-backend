package com.aliwudi.marketplace.backend.notification.service;

import reactor.core.publisher.Mono;

public interface EmailSenderService {
    Mono<Void> sendEmail(String to, String subject, String body);
}
