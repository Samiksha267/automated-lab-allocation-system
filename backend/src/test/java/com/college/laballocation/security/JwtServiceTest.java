package com.college.laballocation.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link JwtService} - no Spring context, no database. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-only-signing-secret-not-used-anywhere-else-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 60);

    @Test
    void generatesTokenContainingExpectedClaims() {
        String token = jwtService.generateToken(42L, "CR");

        Claims claims = jwtService.parseAndValidate(token).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("CR");
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void rejectsTokenSignedWithADifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "a-completely-different-signing-secret-0123456789-abcdef".getBytes(StandardCharsets.UTF_8));
        String tokenSignedByAnotherKey = Jwts.builder()
                .subject("42")
                .claim("role", "LAB_ASSISTANT")
                .signWith(otherKey)
                .compact();

        assertThat(jwtService.parseAndValidate(tokenSignedByAnotherKey)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        JwtService almostExpired = new JwtService(SECRET, 0);
        String token = almostExpired.generateToken(1L, "STUDENT");

        // expiration-minutes=0 means the token's exp equals its iat; by the
        // time parseAndValidate runs, it is already expired.
        assertThat(almostExpired.parseAndValidate(token)).isEmpty();
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(jwtService.parseAndValidate("not-a-real-jwt")).isEmpty();
    }

    @Test
    void expirationSecondsMatchesConfiguredMinutes() {
        JwtService oneHour = new JwtService(SECRET, 60);
        assertThat(oneHour.getExpirationSeconds()).isEqualTo(3600);
    }
}
