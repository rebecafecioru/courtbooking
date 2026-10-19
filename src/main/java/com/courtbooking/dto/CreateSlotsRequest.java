package com.courtbooking.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Lets an org admin generate a batch of slots for a court, e.g.
 * "every Mon/Wed/Fri in June, 09:00-21:00, 1-hour slots".
 * <p>
 * The range is capped at {@link #MAX_DAYS} days. Generation holds the hall's
 * lock while it runs (see TimeSlotService), so one huge request, say two
 * years of 15-minute slots, would hold up every booking in that hall.
 */
public record CreateSlotsRequest(
        @NotNull Long courtId,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        @NotEmpty(message = "Pick at least one day of the week.") Set<java.time.DayOfWeek> daysOfWeek,
        @NotNull LocalTime dailyStartTime,
        @NotNull LocalTime dailyEndTime,
        @Min(value = 15, message = "Slots must be at least 15 minutes long.") int slotDurationMinutes
) {

    public static final int MAX_DAYS = 92; // about three months

    // The checks below skip missing values; @NotNull reports those.

    @JsonIgnore
    @AssertTrue(message = "The end date can't be before the start date.")
    public boolean isDateOrderValid() {
        return fromDate == null || toDate == null || !toDate.isBefore(fromDate);
    }

    @JsonIgnore
    @AssertTrue(message = "You can add at most " + MAX_DAYS + " days of slots at once.")
    public boolean isDateRangeWithinLimit() {
        return fromDate == null || toDate == null || ChronoUnit.DAYS.between(fromDate, toDate) < MAX_DAYS;
    }

    @JsonIgnore
    @AssertTrue(message = "The daily end time must be after the start time.")
    public boolean isDailyTimeOrderValid() {
        return dailyStartTime == null || dailyEndTime == null || dailyEndTime.isAfter(dailyStartTime);
    }

    @JsonIgnore
    @AssertTrue(message = "A slot has to fit between the daily start and end times.")
    public boolean isSlotLengthWithinDay() {
        return dailyStartTime == null || dailyEndTime == null || !dailyEndTime.isAfter(dailyStartTime)
                || slotDurationMinutes <= Duration.between(dailyStartTime, dailyEndTime).toMinutes();
    }
}
