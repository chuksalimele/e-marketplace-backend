// src/main/java/com/marketplace/emarketplacebackend/exception/InsufficientStockException.java
package com.aliwudi.marketplace.backend.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}