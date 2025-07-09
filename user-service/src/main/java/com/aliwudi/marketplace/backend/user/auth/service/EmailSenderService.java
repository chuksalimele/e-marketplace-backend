package com.aliwudi.marketplace.backend.user.auth.service;

import reactor.core.publisher.Mono;

public interface EmailSenderService {
    Mono<Void> sendEmail(String to, String subject, String body);
}