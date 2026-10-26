package com.courtbooking.email;

/**
 * An email to send. BookingService publishes these as Spring events inside
 * its transaction; EmailListener only sends them once that transaction has
 * committed.
 */
public record EmailMessage(String to, String subject, String body) {
}
