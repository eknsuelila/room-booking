package com.portfolio.roombooking.common;

/**
 * Thrown when a requested id does not exist (unknown room or booking).
 * Translated to HTTP <b>404 Not Found</b> by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
