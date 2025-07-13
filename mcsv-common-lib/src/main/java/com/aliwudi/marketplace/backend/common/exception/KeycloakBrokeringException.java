package com.aliwudi.marketplace.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class KeycloakBrokeringException extends RuntimeException {
    public KeycloakBrokeringException(String message) {
        super(message);
    }

    public KeycloakBrokeringException(String message, Throwable cause) {
        super(message, cause);
    }
}