package com.courtbooking.dto;

import com.courtbooking.model.Court;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CourtDtos {

    public record CreateCourtRequest(
            @NotBlank String name,
            @NotBlank String sportType,
            Integer capacity,
            @NotNull Long venueId,
            // Optional: the court this one is part of, e.g. the full hall for
            // "Half A". Booking either one then blocks the other.
            Long parentCourtId
    ) {
    }

    public record CourtResponse(
            Long id, String name, String sportType, Integer capacity,
            Long venueId, String venueName,
            Long parentCourtId // null for a court that isn't part of another
    ) {
        public static CourtResponse from(Court c) {
            return new CourtResponse(c.getId(), c.getName(), c.getSportType(), c.getCapacity(),
                    c.getVenue().getId(), c.getVenue().getName(),
                    c.getParent() != null ? c.getParent().getId() : null);
        }
    }
}
