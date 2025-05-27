package com.aliwudi.marketplace.backend.orderprocessing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.dao.DataIntegrityViolationException; // New import for database errors
import org.springframework.web.bind.annotation.RestController; // Often used with @ControllerAdvice

import java.util.HashMap;
import java.util.Map;

// @ControllerAdvice makes this class capable of handling exceptions across the entire application
// @RestController combines @Controller and @ResponseBody, ensuring JSON responses
@ControllerAdvice
@RestController // Recommended when you return JSON responses from handlers
public class GlobalExceptionHandler {

    // Handler for ResourceNotFoundException (e.g., when an entity isn't found by ID)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request.getDescription(false) // Get the request path (e.g., uri=/api/products/123)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    // Handler for validation errors (e.g., @Valid annotations failing on DTOs)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed", // A general message for validation errors
                request.getDescription(false)
        );
        // You might want to include the 'errors' map in the ErrorResponse if you want more detail.
        // For simplicity, let's keep it separate for now or integrate if the ErrorResponse is expanded.
        // For this handler, returning a map directly is also common.
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    // Handler for Data Integrity Violations (e.g., trying to save a User with a duplicate email)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT, // 409 Conflict
                "Data integrity violation: " + ex.getRootCause().getMessage(), // Get the root cause for more detail
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }


    // Generic handler for all other unhandled exceptions (fallback)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}