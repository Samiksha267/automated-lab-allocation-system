package com.college.laballocation.auth;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * {@code userId} is bound from the SecurityContext principal set by
     * {@link com.college.laballocation.security.JwtAuthenticationFilter} -
     * never from a client-supplied id, since this endpoint answers "who does
     * my *token* say I am," not "look up an arbitrary user."
     */
    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal Long userId) {
        return authService.getCurrentUser(userId);
    }
}
