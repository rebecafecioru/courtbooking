package com.courtbooking.dto;

import com.courtbooking.model.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    // No role field on purpose: anyone can call register, so letting the caller
    // pick a role would let them make themselves an admin. Sign-ups are always
    // PUBLIC_USER; admins are created another way (e.g. DevDataSeeder).
    public record RegisterRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    // organisationId is the organisation an ORG_ADMIN manages, so the frontend
    // knows which courts to show them; null for everyone else.
    public record AuthResponse(String token, String email, String fullName, String role, Long organisationId) {
        public static AuthResponse from(String token, AppUser user) {
            return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole().name(),
                    user.getOrganisation() != null ? user.getOrganisation().getId() : null);
        }
    }
}
