package com.college.laballocation.auth;

import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.security.JwtService;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Authentication use cases: login (credential verification + token issuance)
 * and resolving the currently authenticated user. Deliberately separate from
 * {@link JwtService} (token mechanics) and {@link UserRepository} (persistence)
 * - see docs/15-DESIGN-DECISIONS.md on avoiding one large UserService.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = AppUser.normalizeEmail(request.email());

        AppUser user = userRepository
                .findByEmail(normalizedEmail)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .filter(AppUser::isActive)
                .orElseThrow(() -> {
                    // Never log which specific check failed (unknown email vs wrong
                    // password vs inactive) - only that an attempt failed, and for
                    // which email was attempted (not a secret, useful for audit).
                    log.info("Failed login attempt for email={}", normalizedEmail);
                    return new InvalidCredentialsException();
                });

        log.info("Successful login for userId={} role={}", user.getId(), user.getRole());

        String token = jwtService.generateToken(user.getId(), user.getRole().name());
        return LoginResponse.of(token, jwtService.getExpirationSeconds(), UserSummary.from(user));
    }

    /**
     * By the time a request reaches here, {@code JwtAuthenticationFilter} has
     * already re-verified the user exists and is active (per docs/09-AUTHORIZATION-RBAC.md
     * inactive-account handling) - this lookup is a defensive re-fetch for the
     * current profile data, not a second authorization gate.
     */
    public UserSummary getCurrentUser(Long userId) {
        AppUser user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        return UserSummary.from(user);
    }
}
