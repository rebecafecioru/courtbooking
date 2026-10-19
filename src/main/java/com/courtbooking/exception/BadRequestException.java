package com.courtbooking.exception;

// A request that doesn't make sense (e.g. a date range that ends before it
// starts). Turned into a 400 by GlobalExceptionHandler.
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
