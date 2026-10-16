package com.lims.server.admin;

import com.lims.common.security.AppUser;
import com.lims.server.auth.AuthDtos;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserAdminDtos {

    private UserAdminDtos() {}

    public record CreateUserRequest(
            @Schema(example = "tech.priya")
            @NotBlank(message = "Username is mandatory")
            @Pattern(regexp = "^[a-zA-Z0-9._-]{3,100}$", message = "Username must be 3-100 characters: letters, digits, '.', '_' or '-'")
            String username,

            @Schema(example = "priya.nair@apexpathlab.com")
            @NotBlank(message = "Email is mandatory")
            @Email(message = "Email address is invalid")
            @Size(max = 150)
            String email,

            @Schema(example = "Priya Nair")
            @NotBlank(message = "Full name is mandatory")
            @Size(max = 200)
            String fullName,

            @NotBlank(message = "Password is mandatory")
            @Pattern(regexp = AuthDtos.PASSWORD_POLICY_REGEX, message = AuthDtos.PASSWORD_POLICY_MESSAGE)
            String password,

            @Schema(example = "[\"TECHNICIAN\"]")
            @NotEmpty(message = "At least one role is required")
            Set<@NotBlank String> roles
    ) {}

    public record UpdateRolesRequest(
            @NotEmpty(message = "At least one role is required")
            Set<@NotBlank String> roles
    ) {}

    public record UpdateStatusRequest(
            @NotNull(message = "enabled is mandatory")
            Boolean enabled
    ) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "New password is mandatory")
            @Pattern(regexp = AuthDtos.PASSWORD_POLICY_REGEX, message = AuthDtos.PASSWORD_POLICY_MESSAGE)
            String newPassword
    ) {}

    public record UserDto(
            UUID id,
            String username,
            String email,
            String fullName,
            List<String> roles,
            boolean enabled,
            boolean locked,
            Instant lockedUntil,
            int failedLoginAttempts,
            Instant lastLoginAt,
            Instant createdAt
    ) {
        public static UserDto from(AppUser user) {
            return new UserDto(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    AuthDtos.roleNames(user),
                    Boolean.TRUE.equals(user.getIsEnabled()),
                    user.isLockedAt(Instant.now()),
                    user.getLockedUntil(),
                    user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0,
                    user.getLastLoginAt(),
                    user.getCreatedAt()
            );
        }
    }

    public record AuditLogDto(
            UUID id,
            String entityName,
            String entityId,
            String actionType,
            String changedBy,
            String newData,
            String ipAddress,
            Instant timestamp
    ) {}
}
