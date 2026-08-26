package com.college.laballocation.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails application startup loudly, rather than running quietly with an unsafe JWT secret, whenever the
 * {@code prod} profile is active (Phase 26, PART 10) - forgetting to set a real {@code JWT_SECRET} outside
 * Docker Compose (which already independently guards this path via {@code ${JWT_SECRET:?...}} in
 * {@code docker-compose.yml}) would otherwise fall through to {@code application.yml}'s documented,
 * obviously-non-production fallback secret with no warning at all.
 *
 * <p>Deliberately scoped to {@code @Profile("prod")} only - the {@code dev}/{@code test} profiles keep
 * using the insecure placeholder on purpose (local development must not require generating a real secret
 * just to run {@code ./mvnw spring-boot:run}), and this component never loads for them.
 */
@Component
@Profile("prod")
public class ProductionJwtSecretGuard {

    /** Must match {@code application.yml}'s literal dev-only default exactly - if it ever drifts, this guard silently stops working. */
    static final String INSECURE_DEV_DEFAULT = "dev-only-insecure-jwt-signing-secret-change-me-0123456789";

    /** HS256 requires a key of at least 256 bits (32 bytes); this project's own documented minimum. */
    static final int MIN_SECRET_BYTES = 32;

    public ProductionJwtSecretGuard(@Value("${app.jwt.secret}") String secret) {
        validate(secret);
    }

    static void validate(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. The 'prod' profile refuses to start without one - "
                            + "set a real, randomly-generated secret (e.g. `openssl rand -base64 48`) via the JWT_SECRET environment variable.");
        }
        if (secret.equals(INSECURE_DEV_DEFAULT)) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the development placeholder value. The 'prod' profile refuses to start with it - "
                            + "set a real, randomly-generated secret via the JWT_SECRET environment variable before deploying.");
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is shorter than the minimum " + MIN_SECRET_BYTES
                            + " bytes HS256 requires. Set a longer, randomly-generated secret via the JWT_SECRET environment variable.");
        }
    }
}
