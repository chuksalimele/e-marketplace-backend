// src/main/java/com/aliwudi/marketplace/backend/user/exception/RoleNotFoundException.java
package com.aliwudi.marketplace.backend.user.exception;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String message) {
        super(message);
    }

    public RoleNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}