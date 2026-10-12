package com.courtbooking.dto;

import com.courtbooking.model.Venue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class VenueDtos {

    public record CreateVenueRequest(
            @NotBlank String name,
            @NotBlank String address,
            @NotNull Long organisationId
    ) {
    }

    public record VenueResponse(Long id, String name, String address, Long organisationId, String organisationName) {
        public static VenueResponse from(Venue v) {
            return new VenueResponse(v.getId(), v.getName(), v.getAddress(),
                    v.getOrganisation().getId(), v.getOrganisation().getName());
        }
    }
}
