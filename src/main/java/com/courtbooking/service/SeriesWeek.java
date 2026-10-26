package com.courtbooking.service;

import com.courtbooking.model.TimeSlot;

import java.time.LocalDateTime;

/**
 * One week of a series preview: the slot at that time (null if the court has
 * no slot then) and whether it could be booked right now.
 */
public record SeriesWeek(int week, TimeSlot slot, LocalDateTime startTime, LocalDateTime endTime, Status status) {

    public enum Status {
        FREE,
        TAKEN,    // booked, or covered by a booking or closure on an overlapping space
        CLOSED,   // blocked by an admin
        NO_SLOT,  // the court has no slot at that time (not generated yet)
        STARTED   // already started, so too late to book
    }
}
