package com.courtbooking.exception;

/** Thrown when a user can't join or leave a waitlist, e.g. the slot is actually free. */
public class WaitlistException extends RuntimeException {
    public WaitlistException(String message) {
        super(message);
    }
}
