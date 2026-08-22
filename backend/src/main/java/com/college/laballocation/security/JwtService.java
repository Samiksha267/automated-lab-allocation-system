package com.college.laballocation.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and validates signed (HS256) JWT access tokens. Claims are kept
 * deliberately minimal - {@code sub} (user id), {@code role}, {@code iat},
 * {@code exp} - never a password, password hash, full profile, or CR
 * assignment (see docs/09-AUTHORIZATION-RBAC.md: CR ownership is always
 * resolved from authoritative database data at request time, never trusted
 * from a token claim, since claims are only as fresh as the moment the token
 * was issued).
 */
@Service
public class JwtService {

    private final Key signingKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(Long userId, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(expirationMinutes));

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return Duration.ofMinutes(expirationMinutes).toSeconds();
    }

    /** Returns the parsed claims if the token's signature and expiration are valid, empty otherwise. */
    public java.util.Optional<Claims> parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return java.util.Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            // Deliberately no token/claim content logged - see docs/09-AUTHORIZATION-RBAC.md logging rules.
            return java.util.Optional.empty();
        }
    }
}
