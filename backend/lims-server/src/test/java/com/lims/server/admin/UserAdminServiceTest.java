package com.lims.server.admin;

import com.lims.common.audit.AuditService;
import com.lims.common.exception.DuplicateResourceException;
import com.lims.common.exception.InvalidOperationException;
import com.lims.common.security.AppUser;
import com.lims.common.security.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock AppUserRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @InjectMocks UserAdminService service;

    @BeforeEach
    void signedInAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsUserWithNormalisedRolesAndHashedPassword() {
        when(passwordEncoder.encode("Secret123")).thenReturn("bcrypt");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserAdminDtos.UserDto dto = service.createUser(new UserAdminDtos.CreateUserRequest(
                "tech.priya", "Priya@Lab.com", "Priya Nair", "Secret123", Set.of("technician", "ROLE_PATHOLOGIST")));

        assertThat(dto.roles()).containsExactly("PATHOLOGIST", "TECHNICIAN");
        assertThat(dto.email()).isEqualTo("priya@lab.com");
    }

    @Test
    void rejectsUnknownRole() {
        assertThatThrownBy(() -> service.createUser(new UserAdminDtos.CreateUserRequest(
                "x.user", "x@lab.com", "X", "Secret123", Set.of("SUPERUSER"))))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("SUPERUSER");
    }

    @Test
    void rejectsDuplicateUsername() {
        when(repository.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(new UserAdminDtos.CreateUserRequest(
                "admin", "a2@lab.com", "A", "Secret123", Set.of("ADMIN"))))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void adminCannotLockThemselvesOut() {
        AppUser self = new AppUser(UUID.randomUUID(), "admin", "a@lab.com", "h", "Admin", new HashSet<>(Set.of("ROLE_ADMIN")));
        when(repository.findById(self.getId())).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.setEnabled(self.getId(), false)).isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> service.updateRoles(self.getId(), Set.of("TECHNICIAN"))).isInstanceOf(InvalidOperationException.class);
    }
}
