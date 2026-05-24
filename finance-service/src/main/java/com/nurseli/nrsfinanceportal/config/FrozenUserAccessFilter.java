package com.nurseli.nrsfinanceportal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * loginSuspended kullanıcıların kimlik doğrulamalı isteklerini 403 ile keser.
 */
@Component
@RequiredArgsConstructor
public class FrozenUserAccessFilter extends OncePerRequestFilter {

    private static final String SUSPENDED_ERROR_CODE = "USER_LOGIN_SUSPENDED";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/") || path.startsWith("/swagger-ui/") || path.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository.findByKeycloakUserId(sub).orElse(null);
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!user.isLoginSuspended()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<?> body = ApiResponse.error(Map.of(
                "error", SUSPENDED_ERROR_CODE,
                "message", "Hesabınız askıya alındı. Erişim için destek ile iletişime geçin."
        ));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
