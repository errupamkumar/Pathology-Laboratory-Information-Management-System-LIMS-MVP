package com.lims.server.auth;

import com.lims.common.security.AppUser;
import com.lims.common.security.Roles;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    /**
     * At least 8 characters with a letter and a digit (mirrored by the frontend zod schema).
     */
    public static final String PASSWORD_POLICY_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,128}$";
    public static final String PASSWORD_POLICY_MESSAGE = "Password must be 8-128 characters and contain at least one letter and one digit";

    private AuthDtos() {}

    public record LoginRequest(
            @Schema(example = "admin")
            @NotBlank(message = "Username is mandatory")
            @Size(max = 100)
            String username,

            @Schema(example = "admin123")
            @NotBlank(message = "Password is mandatory")
            @Size(max = 128)
            String password
    ) {}

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserProfile user
    ) {}

    public record UserProfile(
            UUID id,
            String username,
            String fullName,
            String email,
            List<String> roles,
            Instant lastLoginAt
    ) {
        public static UserProfile from(AppUser user) {
            return new UserProfile(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                    roleNames(user), user.getLastLoginAt());
        }
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is mandatory")
            @Size(max = 128)
            String currentPassword,

            @NotBlank(message = "New password is mandatory")
            @Pattern(regexp = PASSWORD_POLICY_REGEX, message = PASSWORD_POLICY_MESSAGE)
            String newPassword
    ) {}

    /**
     * Role names without the {@code ROLE_} prefix, sorted for stable output.
     */
    public static List<String> roleNames(AppUser user) {
        return user.getRoles().stream()
                .map(role -> role.startsWith(Roles.PREFIX) ? role.substring(Roles.PREFIX.length()) : role)
                .sorted()
                .toList();
    }
}
