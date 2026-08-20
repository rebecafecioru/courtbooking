package com.courtbooking.exception;

/** Thrown when a slot is already booked, blocked, or was booked by a concurrent request. */
public class SlotNotAvailableException extends RuntimeException {
    public SlotNotAvailableException(String message) {
        super(message);
    }
}
