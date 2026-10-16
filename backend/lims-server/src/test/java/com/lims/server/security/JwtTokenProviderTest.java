package com.lims.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs256!!";

    final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60, "apexpath-lims");

    @Test
    void roundTripsSubject() {
        String token = provider.generateToken(new TestingAuthenticationToken("technician", "n/a", "ROLE_TECHNICIAN"));

        assertThat(provider.parseUsername(token)).contains("technician");
        assertThat(provider.getExpirationSeconds()).isEqualTo(3600);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtTokenProvider attacker = new JwtTokenProvider("a-completely-different-secret-of-sufficient-len", 60, "apexpath-lims");
        String forged = attacker.generateToken(new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN"));

        assertThat(provider.validateToken(forged)).isFalse();
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        JwtTokenProvider otherIssuer = new JwtTokenProvider(SECRET, 60, "some-other-system");
        String token = otherIssuer.generateToken(new TestingAuthenticationToken("admin", "n/a"));

        assertThat(provider.parseUsername(token)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        JwtTokenProvider expired = new JwtTokenProvider(SECRET, -1, "apexpath-lims");
        String token = expired.generateToken(new TestingAuthenticationToken("admin", "n/a"));

        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void rejectsGarbage() {
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void refusesWeakSigningSecretAtStartup() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", 60, "apexpath-lims"))
                .isInstanceOf(RuntimeException.class);
    }
}
