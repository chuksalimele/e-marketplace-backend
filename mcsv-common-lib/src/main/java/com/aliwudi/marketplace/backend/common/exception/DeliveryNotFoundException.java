package com.aliwudi.marketplace.backend.common.exception;

public class DeliveryNotFoundException extends RuntimeException {
    public DeliveryNotFoundException(String message) {
        super(message);
    }

    public DeliveryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}