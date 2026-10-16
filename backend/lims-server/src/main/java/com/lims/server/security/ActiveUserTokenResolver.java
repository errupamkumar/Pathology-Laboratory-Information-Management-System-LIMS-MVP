package com.lims.server.security;

import com.lims.common.security.AccessTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Turns a bearer token into an active account. The account is re-read on every request, so
 * disabling or locking a user revokes their outstanding tokens immediately.
 * Also serves as the {@link AccessTokenVerifier} for the print-agent WebSocket handshake.
 */
@Component
public class ActiveUserTokenResolver implements AccessTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(ActiveUserTokenResolver.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final LimsUserDetailsService userDetailsService;

    public ActiveUserTokenResolver(JwtTokenProvider jwtTokenProvider, LimsUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    public Optional<UserDetails> resolveActiveUser(String token) {
        Optional<String> username = jwtTokenProvider.parseUsername(token);
        if (username.isEmpty()) {
            return Optional.empty();
        }
        try {
            UserDetails user = userDetailsService.loadUserByUsername(username.get());
            if (!user.isEnabled() || !user.isAccountNonLocked()) {
                log.warn("Rejected token for inactive account {}", username.get());
                return Optional.empty();
            }
            return Optional.of(user);
        } catch (UsernameNotFoundException e) {
            log.warn("Rejected token for unknown account {}", username.get());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> verify(String token) {
        return resolveActiveUser(token).map(UserDetails::getUsername);
    }
}
