package com.portfolio.roombooking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.roombooking.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Writes a JSON {@link ApiError} with status 403 when the SECURITY FILTER CHAIN denies access
 * (an authenticated user without the required role for a URL rule in SecurityConfig).
 *
 * <p>Method-level denials ({@code @PreAuthorize} on controllers) are thrown inside Spring MVC and are
 * handled by {@code GlobalExceptionHandler} instead. Having both guarantees that every 403 in the app
 * has the same JSON shape, wherever it originated.
 */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(403, "Forbidden",
                "You do not have permission to perform this action"));
    }
}
