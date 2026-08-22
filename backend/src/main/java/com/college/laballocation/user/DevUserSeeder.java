package com.college.laballocation.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one demo account per role for local development only. Gated by
 * {@code @Profile("dev")} - never runs with any other active profile, so
 * production never gets predictable seeded credentials (docs/09-AUTHORIZATION-RBAC.md
 * / docs/13-DEVELOPER-SETUP.md). There is deliberately no public self-registration
 * endpoint (docs/02-REQUIREMENTS.md) - this is the only way accounts exist
 * until Lab Assistant CR-management arrives (Phase 4+).
 *
 * <p>Idempotent: skips any email that already exists, so restarting the dev
 * profile repeatedly never fails or duplicates users.
 */
@Component
@Profile("dev")
@Order(1)
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final String labAssistantPassword;
    private final String crPassword;
    private final String studentPassword;

    public DevUserSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.dev-seed.lab-assistant-password}") String labAssistantPassword,
            @Value("${app.dev-seed.cr-password}") String crPassword,
            @Value("${app.dev-seed.student-password}") String studentPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.labAssistantPassword = labAssistantPassword;
        this.crPassword = crPassword;
        this.studentPassword = studentPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("lab.assistant@example.edu", labAssistantPassword, UserRole.LAB_ASSISTANT, "Demo Lab Assistant");
        seed("cr@example.edu", crPassword, UserRole.CR, "Demo CR");
        seed("student@example.edu", studentPassword, UserRole.STUDENT, "Demo Student");
    }

    private void seed(String email, String rawPassword, UserRole role, String displayName) {
        String normalized = AppUser.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            return;
        }
        userRepository.save(new AppUser(normalized, passwordEncoder.encode(rawPassword), role, displayName));
        log.info("Seeded dev-only demo user: email={} role={}", normalized, role);
    }
}
