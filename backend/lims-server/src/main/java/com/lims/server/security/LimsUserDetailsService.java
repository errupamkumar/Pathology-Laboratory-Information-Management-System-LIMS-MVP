package com.lims.server.security;

import com.lims.common.security.AppUser;
import com.lims.common.security.AppUserRepository;
import com.lims.common.security.Roles;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Loads user details from the database for Spring Security authentication.
 */
@Service
public class LimsUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public LimsUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return User.withUsername(appUser.getUsername())
                .password(appUser.getPasswordHash())
                .disabled(!Boolean.TRUE.equals(appUser.getIsEnabled()))
                .accountLocked(appUser.isLockedAt(Instant.now()))
                .authorities(appUser.getRoles().stream()
                        .map(Roles::authority)
                        .map(SimpleGrantedAuthority::new)
                        .toList())
                .build();
    }
}
