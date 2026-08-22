package com.college.laballocation.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT bearer-token security (docs/09-AUTHORIZATION-RBAC.md).
 *
 * <p><b>CSRF is disabled</b> deliberately, not carelessly: CSRF exists to stop
 * a malicious site from making a browser replay an *ambient* credential
 * (a cookie) that the browser attaches automatically. This API never uses a
 * session cookie for authentication - the JWT is sent in an explicit
 * {@code Authorization} header that only this application's own JavaScript
 * ever attaches, and no cross-site request can read or forge that header on
 * the browser's behalf. With no cookie-based session in play
 * ({@link SessionCreationPolicy#STATELESS}), the specific attack CSRF
 * protection defends against does not apply, so enabling it would add
 * friction (a CSRF token dance) with no corresponding protection gained.
 *
 * <p><b>Deny-by-default</b>: only {@code /api/auth/login} and the health
 * endpoints are public; every other request must be authenticated
 * ({@code anyRequest().authenticated()}), so a newly added endpoint is
 * protected unless someone deliberately opens it up.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: one-way, per-password-salted hashing purpose-built for
        // credential storage - see docs/09-AUTHORIZATION-RBAC.md for the full
        // rationale (why hashing, not encryption; salt handling; why it can
        // never be "decrypted").
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/login")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
