package com.nurseli.nrsfinanceportal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorBody;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security kimlik doğrulama ve yetkilendirme hatalarını standart {@link ApiResponse} JSON formatında döner.
 */
@Component
@RequiredArgsConstructor
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, "Authentication required", request);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        writeError(response, HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, "Access denied", request);
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request)
            throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<?> body = ApiResponse.error(ApiErrorBody.of(code, message, request));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
