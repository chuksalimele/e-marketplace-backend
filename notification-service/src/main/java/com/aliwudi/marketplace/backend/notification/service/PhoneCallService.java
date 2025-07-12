package com.aliwudi.marketplace.backend.notification.service;

import reactor.core.publisher.Mono;

/**
 * Interface for initiating phone calls, typically for OTP or verification.
 */
public interface PhoneCallService {
    Mono<Void> initiateCall(String toPhoneNumber, String messageBody); // messageBody might be TwiML/XML or just text to be spoken
}