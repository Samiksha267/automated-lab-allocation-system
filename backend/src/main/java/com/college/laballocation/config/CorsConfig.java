package com.college.laballocation.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Development CORS support for the React frontend. Allowed origins come from
 * {@code app.cors.allowed-origins} (env {@code CORS_ALLOWED_ORIGINS}), never a
 * wildcard - see docs/12-DEPLOYMENT-GUIDE.md for the production configuration
 * approach (set to the real deployed frontend origin, still never "*").
 *
 * <p>Exposed as a {@link CorsConfigurationSource} bean (not a
 * {@code WebMvcConfigurer}) so Spring Security's own CORS handling
 * ({@code http.cors()} in SecurityConfig) uses the exact same allowed-origin
 * list - once Spring Security is on the classpath, its filter chain runs
 * before Spring MVC's CORS handling, so the source of truth has to live here.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
