package com.aliwudi.marketplace.backend.product.exception;

public class InvalidReviewDataException extends RuntimeException {
    public InvalidReviewDataException(String message) {
        super(message);
    }

    public InvalidReviewDataException(String message, Throwable cause) {
        super(message, cause);
    }
}