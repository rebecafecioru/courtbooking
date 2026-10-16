package com.courtbooking.dto;

import com.courtbooking.model.AppUser;
import com.courtbooking.model.Organisation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OrgAdminDtos {

    public record CreateOrgAdminRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank String password,
            @NotNull Long organisationId
    ) {
    }

    public record AssignOrganisationRequest(@NotNull Long organisationId) {
    }

    // Takes the organisation separately: the user's own field may be a lazy proxy
    public record OrgAdminResponse(Long id, String fullName, String email, String role,
                                   Long organisationId, String organisationName) {
        public static OrgAdminResponse from(AppUser u, Organisation org) {
            return new OrgAdminResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(),
                    org.getId(), org.getName());
        }
    }
}
