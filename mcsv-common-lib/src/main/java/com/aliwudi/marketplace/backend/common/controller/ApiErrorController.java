package com.aliwudi.marketplace.backend.common.controller;

import com.aliwudi.marketplace.backend.common.exception.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Implement ErrorController to override Spring Boot's default /error handling
@RestController
public class ApiErrorController implements ErrorController { // Class name changed here

    private static final String ERROR_PATH = "/error";

    @RequestMapping(ERROR_PATH)
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        HttpStatus status = getStatus(request);
        String errorMessage = getErrorMessage(request);
        String requestUri = getRequestUri(request);

        ErrorResponse errorResponse = new ErrorResponse(
                status,
                errorMessage,
                requestUri
        );

        return new ResponseEntity<>(errorResponse, status);
    }

    // --- Helper methods to extract error details from the request ---

    private HttpStatus getStatus(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            return HttpStatus.valueOf(statusCode);
        } catch (IllegalArgumentException ex) {
            return HttpStatus.INTERNAL_SERVER_ERROR; // Fallback for invalid status codes
        }
    }

    private String getErrorMessage(HttpServletRequest request) {
        Object errorException = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (errorException instanceof Throwable) {
            return ((Throwable) errorException).getMessage();
        }
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        if (errorMessage != null) {
            return errorMessage.toString();
        }
        return "An unexpected error occurred.";
    }

    private String getRequestUri(HttpServletRequest request) {
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (requestUri != null) {
            return requestUri.toString();
        }
        return "unknown path";
    }
}