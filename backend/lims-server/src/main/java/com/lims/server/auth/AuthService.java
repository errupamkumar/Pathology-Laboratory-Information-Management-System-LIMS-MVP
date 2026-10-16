package com.lims.server.auth;

import com.lims.common.exception.InvalidOperationException;
import com.lims.common.exception.ResourceNotFoundException;
import com.lims.common.security.AppUser;
import com.lims.common.security.AppUserRepository;
import com.lims.server.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       LoginAttemptService loginAttemptService,
                       AppUserRepository appUserRepository,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptService = loginAttemptService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Unknown usernames and wrong passwords produce the same error, so the response does not
     * reveal which accounts exist.
     */
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String username = request.username().trim();
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
        } catch (BadCredentialsException e) {
            try {
                loginAttemptService.recordFailure(username);
            } catch (RuntimeException trackingFailure) {
                // e.g. optimistic-lock clash between concurrent failures; never mask the auth error
                log.warn("Could not record failed sign-in for {}: {}", username, trackingFailure.getMessage());
            }
            throw e;
        }

        loginAttemptService.recordSuccess(authentication.getName());
        AppUser user = appUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", authentication.getName()));

        log.info("SECURITY: {} signed in", user.getUsername());
        return new AuthDtos.LoginResponse(
                jwtTokenProvider.generateToken(authentication),
                "Bearer",
                jwtTokenProvider.getExpirationSeconds(),
                AuthDtos.UserProfile.from(user)
        );
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserProfile profile(String username) {
        return appUserRepository.findByUsername(username)
                .map(AuthDtos.UserProfile::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    @Transactional
    public void changePassword(String username, AuthDtos.ChangePasswordRequest request) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        // 400 rather than 401: a 401 would make the client discard a perfectly valid session
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidOperationException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidOperationException("New password must differ from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        log.info("SECURITY: {} changed their password", username);
    }
}
