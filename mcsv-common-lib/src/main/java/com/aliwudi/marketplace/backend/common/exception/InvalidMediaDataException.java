package com.aliwudi.marketplace.backend.common.exception;

public class InvalidMediaDataException extends RuntimeException {
    public InvalidMediaDataException(String message) {
        super(message);
    }

    public InvalidMediaDataException(String message, Throwable cause) {
        super(message, cause);
    }
}