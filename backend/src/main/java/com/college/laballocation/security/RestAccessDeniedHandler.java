package com.college.laballocation.security;

import com.college.laballocation.common.ApiErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Authenticated but insufficient role/permission -&gt; 403 FORBIDDEN in the
 * project's standard JSON error shape, not Spring Security's default HTML
 * error page (docs/09-AUTHORIZATION-RBAC.md: 401 vs 403 distinction).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body =
                ApiErrorResponse.of("FORBIDDEN", "You do not have permission to perform this action.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
