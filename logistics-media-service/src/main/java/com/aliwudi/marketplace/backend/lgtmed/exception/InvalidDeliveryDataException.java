package com.aliwudi.marketplace.backend.lgtmed.exception;

public class InvalidDeliveryDataException extends RuntimeException {
    public InvalidDeliveryDataException(String message) {
        super(message);
    }

    public InvalidDeliveryDataException(String message, Throwable cause) {
        super(message, cause);
    }
}