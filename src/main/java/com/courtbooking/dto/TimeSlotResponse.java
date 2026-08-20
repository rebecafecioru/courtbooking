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
        String status
) {
    public static TimeSlotResponse from(TimeSlot t) {
        return new TimeSlotResponse(
                t.getId(),
                t.getCourt().getId(),
                t.getCourt().getName(),
                t.getCourt().getSportType(),
                t.getCourt().getVenue().getName(),
                t.getStartTime(),
                t.getEndTime(),
                t.getStatus().name()
        );
    }
}
