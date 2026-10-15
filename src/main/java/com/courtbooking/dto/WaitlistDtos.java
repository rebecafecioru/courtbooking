package com.courtbooking.dto;

import com.courtbooking.model.WaitlistEntry;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class WaitlistDtos {

    public record WaitlistRequest(@NotNull Long timeSlotId) {
    }

    public record WaitlistResponse(
            Long id,
            Long timeSlotId,
            String courtName,
            String venueName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            long position // 1 means next in line
    ) {
        public static WaitlistResponse from(WaitlistEntry w, long position) {
            return new WaitlistResponse(
                    w.getId(),
                    w.getTimeSlot().getId(),
                    w.getTimeSlot().getCourt().getName(),
                    w.getTimeSlot().getCourt().getVenue().getName(),
                    w.getTimeSlot().getStartTime(),
                    w.getTimeSlot().getEndTime(),
                    position
            );
        }
    }
}
