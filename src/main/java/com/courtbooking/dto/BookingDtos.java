package com.courtbooking.dto;

import com.courtbooking.model.Booking;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class BookingDtos {

    public record BookingRequest(@NotNull Long timeSlotId) {
    }

    public record BookingResponse(
            Long id,
            Long timeSlotId,
            String courtName,
            String venueName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String bookedByName,
            String status
    ) {
        public static BookingResponse from(Booking b) {
            return new BookingResponse(
                    b.getId(),
                    b.getTimeSlot().getId(),
                    b.getTimeSlot().getCourt().getName(),
                    b.getTimeSlot().getCourt().getVenue().getName(),
                    b.getTimeSlot().getStartTime(),
                    b.getTimeSlot().getEndTime(),
                    b.getBookedBy().getFullName(),
                    b.getStatus().name()
            );
        }
    }
}
