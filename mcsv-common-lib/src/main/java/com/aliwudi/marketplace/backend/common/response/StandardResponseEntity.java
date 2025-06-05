/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.common.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

// This class extends ResponseEntity and fixes its body type to ApiResponse<?>


// This means the body of any StandardResponseEntity will ALWAYS be an ApiResponse.


public class StandardResponseEntity<T> extends ResponseEntity<ApiResponse<T>> {

    public StandardResponseEntity(ApiResponse<T> body, HttpStatus status) {
        super(body, status);
    }

    public StandardResponseEntity(ApiResponse<T> body, MultiValueMap<String, String> headers, HttpStatus status) {
        super(body, headers, status);
    }

    // --- Convenience methods for common responses ---

    // Success response
    public static <T> StandardResponseEntity ok(T data) {
        return new StandardResponseEntity<>(ApiResponse.success(data, null, HttpStatus.OK), HttpStatus.OK);
    }
    
    // Success response
    public static <T> StandardResponseEntity ok(T data, String message) {
        return new StandardResponseEntity<>(ApiResponse.success(data, message, HttpStatus.OK), HttpStatus.OK);
    }

    // Created response
    public static <T> StandardResponseEntity created(T data, String message) {
        return new StandardResponseEntity<>(ApiResponse.success(data, message, HttpStatus.CREATED), HttpStatus.CREATED);
    }

    // Not Found error response
    public static StandardResponseEntity notFound(String errorMessage) {
        return new StandardResponseEntity<>(ApiResponse.error(errorMessage, HttpStatus.NOT_FOUND), HttpStatus.NOT_FOUND);
    }

    // Bad Request error response
    public static StandardResponseEntity badRequest(String errorMessage) {
        return new StandardResponseEntity<>(ApiResponse.error(errorMessage, HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    // Internal Server Error response
    public static StandardResponseEntity internalServerError(String errorMessage) {
        return new StandardResponseEntity<>(ApiResponse.error(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
        // Unauthorized response (NEW)
    public static StandardResponseEntity unauthorized(String errorMessage) {
        return new StandardResponseEntity<>(ApiResponse.error(errorMessage, HttpStatus.UNAUTHORIZED), HttpStatus.UNAUTHORIZED);
    }
}
