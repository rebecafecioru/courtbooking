package com.courtbooking.dto;

import com.courtbooking.dto.BookingDtos.BookingResponse;
import com.courtbooking.service.SeriesWeek;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class SeriesDtos {

    // The slots to book together, usually every week from a preview, or just
    // the free ones. All or nothing: if any is taken, none are booked.
    public record SeriesBookingRequest(@NotEmpty List<@NotNull Long> timeSlotIds) {
    }

    public record SeriesWeekResponse(
            int week,
            Long timeSlotId, // null when the court has no slot that week
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status
    ) {
        public static SeriesWeekResponse from(SeriesWeek w) {
            return new SeriesWeekResponse(w.week(), w.slot() != null ? w.slot().getId() : null,
                    w.startTime(), w.endTime(), w.status().name());
        }
    }

    public record SeriesBookingResponse(Long seriesId, List<BookingResponse> bookings) {
    }

    public record SeriesCancelResponse(int cancelledWeeks) {
    }
}
