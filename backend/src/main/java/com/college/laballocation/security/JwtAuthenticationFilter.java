package com.college.laballocation.security;

import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Request lifecycle (docs/09-AUTHORIZATION-RBAC.md): extract Bearer token -&gt;
 * validate signature/expiration (JwtService) -&gt; re-fetch the user fresh from
 * the database on every request -&gt; reject if missing/inactive -&gt; populate
 * SecurityContext with the user's *current* role (not the token's role claim -
 * a stale claim must never outlive a role change) -&gt; continue to the
 * controller. If any step fails, no Authentication is set and the request
 * proceeds unauthenticated - Spring Security's own entry point then returns a
 * uniform 401 for any endpoint that requires authentication.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            authenticate(token, request);
        }

        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        Optional<Claims> claims = jwtService.parseAndValidate(token);
        if (claims.isEmpty()) {
            log.debug("Rejected request with invalid or expired JWT");
            return;
        }

        Long userId;
        try {
            userId = Long.valueOf(claims.get().getSubject());
        } catch (NumberFormatException e) {
            log.debug("JWT subject was not a valid user id");
            return;
        }

        Optional<AppUser> user = userRepository.findById(userId).filter(AppUser::isActive);
        if (user.isEmpty()) {
            log.debug("Rejected request: user id from JWT no longer exists or is inactive");
            return;
        }

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name()));
        var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
