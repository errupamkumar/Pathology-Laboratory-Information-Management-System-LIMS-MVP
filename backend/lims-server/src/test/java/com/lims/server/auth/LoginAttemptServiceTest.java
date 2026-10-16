package com.lims.server.auth;

import com.lims.common.audit.AuditService;
import com.lims.common.enums.AuditAction;
import com.lims.common.security.AppUser;
import com.lims.common.security.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock AppUserRepository repository;
    @Mock AuditService auditService;

    LoginAttemptService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(repository, auditService, 3, Duration.ofMinutes(15));
        user = new AppUser(UUID.randomUUID(), "technician", "t@lab.com", "hash", "Tech", Set.of("ROLE_TECHNICIAN"));
        when(repository.findByUsername("technician")).thenReturn(Optional.of(user));
    }

    @Test
    void locksAccountTemporarilyAfterMaxFailures() {
        service.recordFailure("technician");
        service.recordFailure("technician");
        assertThat(user.isLockedAt(Instant.now())).isFalse();

        service.recordFailure("technician");

        assertThat(user.isLockedAt(Instant.now())).isTrue();
        assertThat(user.isLockedAt(Instant.now().plus(Duration.ofMinutes(16)))).isFalse();
        verify(auditService).recordSecurityEvent(eq("technician"), eq(AuditAction.ACCOUNT_LOCKED), any());
    }

    @Test
    void successResetsCountersAndStampsLastLogin() {
        service.recordFailure("technician");

        service.recordSuccess("technician");

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(user.getIsAccountLocked()).isFalse();
    }
}
