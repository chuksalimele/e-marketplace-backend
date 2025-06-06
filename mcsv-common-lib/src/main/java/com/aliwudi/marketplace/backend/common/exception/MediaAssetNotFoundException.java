package com.aliwudi.marketplace.backend.common.exception;

public class MediaAssetNotFoundException extends RuntimeException {
    public MediaAssetNotFoundException(String message) {
        super(message);
    }

    public MediaAssetNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}