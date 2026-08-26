package com.college.laballocation.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionJwtSecretGuardTest {

    @Test
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> ProductionJwtSecretGuard.validate(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is not set");
    }

    @Test
    void rejectsTheDevelopmentPlaceholderSecret() {
        assertThatThrownBy(() -> ProductionJwtSecretGuard.validate(ProductionJwtSecretGuard.INSECURE_DEV_DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development placeholder");
    }

    @Test
    void rejectsATooShortSecret() {
        assertThatThrownBy(() -> ProductionJwtSecretGuard.validate("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimum 32 bytes");
    }

    @Test
    void acceptsARealLongRandomSecret() {
        String realSecret = "x".repeat(48);
        assertThatCode(() -> ProductionJwtSecretGuard.validate(realSecret)).doesNotThrowAnyException();
    }
}
