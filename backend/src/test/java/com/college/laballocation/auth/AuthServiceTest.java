package com.college.laballocation.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.college.laballocation.security.JwtService;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthService} - repository, encoder, and JWT service
 * are mocked so this exercises only the login decision logic (does not
 * over-mock security internals; each mock stands in for a real collaborator
 * with a single, obvious responsibility).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    private AppUser activeUser(UserRole role) {
        return new AppUser("cr@example.edu", "hashed-password", role, "Test User");
    }

    @Test
    void successfulLoginReturnsTokenAndSafeUserSummary() {
        AppUser user = activeUser(UserRole.CR);
        when(userRepository.findByEmail("cr@example.edu")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(any(), anyString())).thenReturn("signed.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest(" CR@Example.edu ", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("cr@example.edu");
        assertThat(response.user().role()).isEqualTo("CR");
    }

    @Test
    void wrongPasswordIsRejectedWithGenericInvalidCredentials() {
        AppUser user = activeUser(UserRole.CR);
        when(userRepository.findByEmail("cr@example.edu")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("cr@example.edu", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unknownEmailIsRejectedWithTheSameGenericError() {
        when(userRepository.findByEmail("nobody@example.edu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.edu", "whatever1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void inactiveUserIsRejectedEvenWithCorrectPassword() {
        AppUser user = activeUser(UserRole.STUDENT);
        user.deactivate();
        when(userRepository.findByEmail("cr@example.edu")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("cr@example.edu", "correct-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
