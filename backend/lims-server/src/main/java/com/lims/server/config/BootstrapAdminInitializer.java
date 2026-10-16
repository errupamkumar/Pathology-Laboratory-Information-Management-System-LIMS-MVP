package com.lims.server.config;

import com.lims.common.security.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards against running with the well-known seeded admin password ({@code admin123}).
 * <ul>
 *   <li>{@code LIMS_BOOTSTRAP_ADMIN_PASSWORD} set: the default password is replaced on startup.</li>
 *   <li>otherwise, with {@code lims.security.allow-default-admin-password=false} (prod): startup fails.</li>
 *   <li>otherwise: a warning is logged on every start.</li>
 * </ul>
 */
@Component
@Order(1)
public class BootstrapAdminInitializer implements ApplicationRunner {

    static final String ADMIN_USERNAME = "admin";
    static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapPassword;
    private final boolean allowDefaultPassword;

    public BootstrapAdminInitializer(AppUserRepository appUserRepository,
                                     PasswordEncoder passwordEncoder,
                                     @Value("${lims.security.bootstrap-admin-password:}") String bootstrapPassword,
                                     @Value("${lims.security.allow-default-admin-password:true}") boolean allowDefaultPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapPassword = bootstrapPassword;
        this.allowDefaultPassword = allowDefaultPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        appUserRepository.findByUsername(ADMIN_USERNAME)
                .filter(admin -> passwordEncoder.matches(DEFAULT_ADMIN_PASSWORD, admin.getPasswordHash()))
                .ifPresent(admin -> {
                    if (bootstrapPassword != null && !bootstrapPassword.isBlank()) {
                        if (bootstrapPassword.length() < 12) {
                            throw new IllegalStateException("LIMS_BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters");
                        }
                        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
                        log.info("SECURITY: default admin password replaced from LIMS_BOOTSTRAP_ADMIN_PASSWORD");
                    } else if (!allowDefaultPassword) {
                        throw new IllegalStateException("Refusing to start: the 'admin' account still uses the default password. "
                                + "Set LIMS_BOOTSTRAP_ADMIN_PASSWORD (min 12 chars) to rotate it on startup.");
                    } else {
                        log.warn("SECURITY WARNING: the 'admin' account uses the default password '{}'. "
                                + "Change it via POST /api/v1/auth/change-password before go-live.", DEFAULT_ADMIN_PASSWORD);
                    }
                });
    }
}
