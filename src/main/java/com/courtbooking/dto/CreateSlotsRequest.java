package com.courtbooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Lets an org admin generate a batch of slots for a court, e.g.
 * "every Mon/Wed/Fri in June, 09:00-21:00, 1-hour slots".
 */
public record CreateSlotsRequest(
        @NotNull Long courtId,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        @NotNull Set<java.time.DayOfWeek> daysOfWeek,
        @NotNull LocalTime dailyStartTime,
        @NotNull LocalTime dailyEndTime,
        @Min(15) int slotDurationMinutes
) {
}
