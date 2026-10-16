package com.lims.server.auth;

import com.lims.common.audit.AuditService;
import com.lims.common.enums.AuditAction;
import com.lims.common.security.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Brute-force protection: after {@code maxFailedAttempts} consecutive bad passwords the account
 * is locked for {@code lockDuration}; a successful sign-in resets the counter.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(AppUserRepository appUserRepository,
                               AuditService auditService,
                               @Value("${lims.security.lockout.max-failed-attempts:5}") int maxFailedAttempts,
                               @Value("${lims.security.lockout.duration:PT15M}") Duration lockDuration) {
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = lockDuration;
    }

    @Transactional
    public void recordFailure(String username) {
        auditService.recordSecurityEvent(username, AuditAction.LOGIN_FAILURE, null);
        appUserRepository.findByUsername(username).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= maxFailedAttempts) {
                Instant until = Instant.now().plus(lockDuration);
                user.setIsAccountLocked(true);
                user.setLockedUntil(until);
                user.setFailedLoginAttempts(0);
                log.warn("SECURITY: account {} locked until {} after {} failed sign-in attempts", username, until, attempts);
                auditService.recordSecurityEvent(username, AuditAction.ACCOUNT_LOCKED, Map.of("lockedUntil", until.toString()));
            } else {
                user.setFailedLoginAttempts(attempts);
                log.warn("SECURITY: failed sign-in for {} ({}/{})", username, attempts, maxFailedAttempts);
            }
        });
    }

    @Transactional
    public void recordSuccess(String username) {
        appUserRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setIsAccountLocked(false);
            user.setLockedUntil(null);
            user.setLastLoginAt(Instant.now());
        });
        auditService.recordSecurityEvent(username, AuditAction.LOGIN_SUCCESS, null);
    }
}
