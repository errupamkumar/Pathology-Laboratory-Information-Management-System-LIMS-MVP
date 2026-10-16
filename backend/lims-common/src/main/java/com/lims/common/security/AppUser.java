package com.lims.common.security;

import com.lims.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Application user entity for JWT-based authentication.
 * Roles: ROLE_ADMIN, ROLE_PATHOLOGIST, ROLE_TECHNICIAN, ROLE_RECEPTIONIST
 */
@Entity
@Table(name = "app_users", indexes = {
        @Index(name = "idx_users_username", columnList = "username"),
        @Index(name = "idx_users_email", columnList = "email")
})
public class AppUser extends BaseEntity {

    @NotBlank
    @Size(min = 3, max = 100)
    @Column(name = "username", unique = true, nullable = false, length = 100)
    private String username;

    @NotBlank
    @Email
    @Column(name = "email", unique = true, nullable = false, length = 150)
    private String email;

    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotBlank
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "is_account_locked", nullable = false)
    private Boolean isAccountLocked = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * End of a temporary brute-force lockout; null while {@code isAccountLocked} is an administrative lock.
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 50)
    private Set<String> roles = new HashSet<>();

    public AppUser() {}

    public AppUser(UUID id, String username, String email, String passwordHash, String fullName, Set<String> roles) {
        super(id);
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.roles = roles != null ? roles : new HashSet<>();
    }

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean enabled) { this.isEnabled = enabled; }

    public Boolean getIsAccountLocked() { return isAccountLocked; }
    public void setIsAccountLocked(Boolean accountLocked) { this.isAccountLocked = accountLocked; }

    public Integer getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(Integer failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    /**
     * Locked if administratively locked, or temporarily locked and the lockout has not yet expired.
     */
    public boolean isLockedAt(Instant now) {
        if (!Boolean.TRUE.equals(isAccountLocked)) {
            return false;
        }
        return lockedUntil == null || now.isBefore(lockedUntil);
    }
}
