package com.courtbooking.dto;

import com.courtbooking.model.TimeSlot;

import java.time.LocalDateTime;

public record TimeSlotResponse(
        Long id,
        Long courtId,
        String courtName,
        String sportType,
        String venueName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        // For the logged-in viewer: they booked this slot / are on its waitlist.
        // Always false when nobody is logged in.
        boolean yours,
        boolean onWaitlist
) {
    public static TimeSlotResponse from(TimeSlot t) {
        return from(t, false, false);
    }

    public static TimeSlotResponse from(TimeSlot t, boolean yours, boolean onWaitlist) {
        return new TimeSlotResponse(
                t.getId(),
                t.getCourt().getId(),
                t.getCourt().getName(),
                t.getCourt().getSportType(),
                t.getCourt().getVenue().getName(),
                t.getStartTime(),
                t.getEndTime(),
                t.getStatus().name(),
                yours,
                onWaitlist
        );
    }
}
