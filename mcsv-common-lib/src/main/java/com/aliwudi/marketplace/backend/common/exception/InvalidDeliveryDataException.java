package com.aliwudi.marketplace.backend.common.exception;

public class InvalidDeliveryDataException extends RuntimeException {
    public InvalidDeliveryDataException(String message) {
        super(message);
    }

    public InvalidDeliveryDataException(String message, Throwable cause) {
        super(message, cause);
    }
}