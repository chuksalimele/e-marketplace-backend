package com.aliwudi.marketplace.backend.common.exception;

public class OtpValidationException extends RuntimeException {
    public OtpValidationException(String message) {
        super(message);
    }
    public OtpValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}