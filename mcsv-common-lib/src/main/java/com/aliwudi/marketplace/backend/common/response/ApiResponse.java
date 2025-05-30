/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.response;
// src/main/java/com/aliwudi/marketplace/backend/common/response/ApiResponse.java
// Renaming to ApiResponse is common to avoid naming conflicts with
// java.awt.Response or other 'Response' classes.

import lombok.AllArgsConstructor;
import lombok.Builder; // Optional, but useful for building instances
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // Provides a builder pattern for easy object creation
public class ApiResponse<T> { // <T> makes this class generic to hold any data type
    private LocalDateTime timestamp;
    private int status;
    private String message;
    private T data; // The actual data payload (can be a single object, a list, null, etc.)
    private String error; // For error messages

    // --- Convenience constructors/methods ---

    public static <T> ApiResponse success(T data, String message, HttpStatus httpStatus) {
        return ApiResponse.<T>builder()
                .timestamp(LocalDateTime.now())
                .status(httpStatus.value())
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse success(T data, String message) {
        return success(data, message, HttpStatus.OK);
    }

    public static ApiResponse<Void> error(String message, HttpStatus httpStatus) {
        return ApiResponse.<Void>builder()
                .timestamp(LocalDateTime.now())
                .status(httpStatus.value())
                .message("Error") // Standard message for error
                .error(message) // Specific error details
                .data(null)
                .build();
    }
}
