package com.lims.server.admin;

import com.lims.common.audit.AuditService;
import com.lims.common.enums.AuditAction;
import com.lims.common.exception.DuplicateResourceException;
import com.lims.common.exception.InvalidOperationException;
import com.lims.common.exception.ResourceNotFoundException;
import com.lims.common.security.AppUser;
import com.lims.common.security.AppUserRepository;
import com.lims.common.security.CurrentUser;
import com.lims.common.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Staff account provisioning. AppUser is deliberately not {@code @Auditable} (the entity
 * snapshot would contain the password hash), so changes are audited here with explicit,
 * sanitised details.
 */
@Service
@Transactional
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAdminService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<UserAdminDtos.UserDto> listUsers(Pageable pageable) {
        return appUserRepository.findAll(pageable).map(UserAdminDtos.UserDto::from);
    }

    public UserAdminDtos.UserDto createUser(UserAdminDtos.CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (appUserRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        if (appUserRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        AppUser user = new AppUser(UUID.randomUUID(), username, email,
                passwordEncoder.encode(request.password()), request.fullName().trim(), toAuthorities(request.roles()));
        AppUser saved = appUserRepository.save(user);

        audit(saved, AuditAction.INSERT, Map.of("roles", saved.getRoles(), "email", saved.getEmail()));
        log.info("SECURITY: {} created user {} with roles {}", CurrentUser.username(), username, saved.getRoles());
        return UserAdminDtos.UserDto.from(saved);
    }

    public UserAdminDtos.UserDto updateRoles(UUID id, Set<String> roles) {
        AppUser user = find(id);
        Set<String> authorities = toAuthorities(roles);
        if (isSelf(user) && !authorities.contains(Roles.authority(Roles.ADMIN))) {
            throw new InvalidOperationException("You cannot remove your own ADMIN role");
        }
        user.getRoles().clear();
        user.getRoles().addAll(authorities);
        audit(user, AuditAction.UPDATE, Map.of("roles", authorities));
        return UserAdminDtos.UserDto.from(user);
    }

    public UserAdminDtos.UserDto setEnabled(UUID id, boolean enabled) {
        AppUser user = find(id);
        if (isSelf(user) && !enabled) {
            throw new InvalidOperationException("You cannot disable your own account");
        }
        user.setIsEnabled(enabled);
        audit(user, AuditAction.UPDATE, Map.of("enabled", enabled));
        return UserAdminDtos.UserDto.from(user);
    }

    public UserAdminDtos.UserDto unlock(UUID id) {
        AppUser user = find(id);
        user.setIsAccountLocked(false);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        audit(user, AuditAction.UPDATE, Map.of("unlocked", true));
        return UserAdminDtos.UserDto.from(user);
    }

    public void resetPassword(UUID id, String newPassword) {
        AppUser user = find(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        audit(user, AuditAction.UPDATE, Map.of("passwordReset", true));
    }

    private AppUser find(UUID id) {
        return appUserRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private static boolean isSelf(AppUser user) {
        return user.getUsername().equals(CurrentUser.username());
    }

    private static Set<String> toAuthorities(Set<String> roles) {
        Set<String> authorities = roles.stream()
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .map(r -> r.startsWith(Roles.PREFIX) ? r.substring(Roles.PREFIX.length()) : r)
                .peek(r -> {
                    if (!Roles.ALL.contains(r)) {
                        throw new InvalidOperationException("Unknown role '" + r + "'. Allowed: " + Roles.ALL);
                    }
                })
                .map(Roles::authority)
                .collect(Collectors.toCollection(java.util.HashSet::new));
        if (authorities.isEmpty()) {
            throw new InvalidOperationException("At least one role is required");
        }
        return authorities;
    }

    private void audit(AppUser user, AuditAction action, Map<String, Object> details) {
        auditService.recordAudit("AppUser", user.getUsername(), action, null,
                Map.of("username", user.getUsername(), "changes", details));
    }
}
