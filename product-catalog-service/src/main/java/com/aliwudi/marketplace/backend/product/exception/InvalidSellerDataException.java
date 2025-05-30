package com.aliwudi.marketplace.backend.product.exception;

public class InvalidSellerDataException extends RuntimeException {
    public InvalidSellerDataException(String message) {
        super(message);
    }

    public InvalidSellerDataException(String message, Throwable cause) {
        super(message, cause);
    }
}