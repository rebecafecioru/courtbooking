package com.courtbooking.dto;

import com.courtbooking.model.Organisation;

public record OrganisationResponse(
        Long id,
        String name,
        String description,
        String contactEmail
) {
    public static OrganisationResponse from(Organisation o) {
        return new OrganisationResponse(o.getId(), o.getName(), o.getDescription(), o.getContactEmail());
    }
}
