package com.courtbooking.dto;

import com.courtbooking.model.AppUser;
import com.courtbooking.model.Organisation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.courtbooking.dto.AuthDtos.MAX_PASSWORD_LENGTH;
import static com.courtbooking.dto.AuthDtos.MIN_PASSWORD_LENGTH;
import static com.courtbooking.dto.AuthDtos.PASSWORD_RULE;

public class OrgAdminDtos {

    public record CreateOrgAdminRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH, message = PASSWORD_RULE)
            String password,
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
