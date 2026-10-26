package com.courtbooking.dto;

import com.courtbooking.model.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    // 72 is the most bcrypt looks at: anything longer would be silently cut
    // short, so two different long passwords could both work.
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 72;
    public static final String PASSWORD_RULE = "Password must be 8 to 72 characters long";

    // No role field on purpose: anyone can call register, so letting the caller
    // pick a role would let them make themselves an admin. Sign-ups are always
    // PUBLIC_USER; admins are created another way (e.g. DevDataSeeder).
    public record RegisterRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH, message = PASSWORD_RULE)
            String password
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
