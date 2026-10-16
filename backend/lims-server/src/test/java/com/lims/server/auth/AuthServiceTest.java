package com.lims.server.auth;

import com.lims.common.exception.InvalidOperationException;
import com.lims.common.security.AppUser;
import com.lims.common.security.AppUserRepository;
import com.lims.server.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock LoginAttemptService loginAttemptService;
    @Mock AppUserRepository appUserRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthService service;

    final AppUser user = new AppUser(UUID.randomUUID(), "reception", "r@lab.com", "hash", "Anita", Set.of("ROLE_RECEPTIONIST"));

    @Test
    void successfulLoginReturnsTokenAndProfileWithoutRolePrefix() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("reception", null, "ROLE_RECEPTIONIST");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(appUserRepository.findByUsername("reception")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(auth)).thenReturn("jwt");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(3600L);

        AuthDtos.LoginResponse response = service.login(new AuthDtos.LoginRequest(" reception ", "secret"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.user().roles()).containsExactly("RECEPTIONIST");
        verify(loginAttemptService).recordSuccess("reception");
    }

    @Test
    void badCredentialsAreRecordedAndRethrown() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("reception", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verify(loginAttemptService).recordFailure("reception");
    }

    @Test
    void failureTrackingErrorDoesNotMaskAuthenticationError() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        doThrow(new IllegalStateException("optimistic lock")).when(loginAttemptService).recordFailure("reception");

        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("reception", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        when(appUserRepository.findByUsername("reception")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword("reception", new AuthDtos.ChangePasswordRequest("wrong", "NewPass123")))
                .isInstanceOf(InvalidOperationException.class);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void changePasswordStoresNewHash() {
        when(appUserRepository.findByUsername("reception")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        service.changePassword("reception", new AuthDtos.ChangePasswordRequest("current", "NewPass123"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    }
}
