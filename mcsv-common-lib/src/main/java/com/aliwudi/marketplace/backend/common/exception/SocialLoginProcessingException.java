package com.aliwudi.marketplace.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class SocialLoginProcessingException extends RuntimeException {
    public SocialLoginProcessingException(String message) {
        super(message);
    }

    public SocialLoginProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}