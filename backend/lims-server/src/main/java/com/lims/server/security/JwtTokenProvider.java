package com.lims.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and validates HMAC-SHA256 signed access tokens. The signing secret comes from
 * {@code lims.security.jwt.secret} (env {@code LIMS_JWT_SECRET}); a secret shorter than
 * 256 bits is rejected at startup by {@link Keys#hmacShaKeyFor}.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    static final String ROLES_CLAIM = "roles";

    private final SecretKey secretKey;
    private final Duration ttl;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${lims.security.jwt.secret}") String secret,
            @Value("${lims.security.jwt.expiration-minutes:480}") long expirationMinutes,
            @Value("${lims.security.jwt.issuer:apexpath-lims}") String issuer) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(expirationMinutes);
        this.issuer = issuer;
    }

    public String generateToken(Authentication authentication) {
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(authentication.getName())
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * @return the subject of a correctly signed, unexpired token from this issuer
     */
    public Optional<String> parseUsername(String token) {
        return parseClaims(token).map(Claims::getSubject);
    }

    public boolean validateToken(String token) {
        return parseClaims(token).isPresent();
    }

    public long getExpirationSeconds() {
        return ttl.toSeconds();
    }

    private Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
