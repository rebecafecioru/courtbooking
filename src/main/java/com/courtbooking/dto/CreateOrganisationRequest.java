package com.courtbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * What a super admin sends to create an organisation. Only these fields can be
 * set, so a request can't sneak in anything else on the entity (like venues).
 */
public record CreateOrganisationRequest(
        @NotBlank String name,
        String description,
        @Email String contactEmail
) {
}
