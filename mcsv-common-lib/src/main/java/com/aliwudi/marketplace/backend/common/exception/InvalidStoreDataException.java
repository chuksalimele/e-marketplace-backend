package com.aliwudi.marketplace.backend.common.exception;

public class InvalidStoreDataException extends RuntimeException {
    public InvalidStoreDataException(String message) {
        super(message);
    }

    public InvalidStoreDataException(String message, Throwable cause) {
        super(message, cause);
    }
}