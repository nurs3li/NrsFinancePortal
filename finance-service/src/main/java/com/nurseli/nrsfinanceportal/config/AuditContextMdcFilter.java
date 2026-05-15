package com.nurseli.nrsfinanceportal.config;

import com.nurseli.nrsfinanceportal.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Her istekte audit logları için userId (DB), actionType (URI tabanlı), username (JWT) MDC/ThreadContext'e yazılır.
 * KafkaLogAppender bu alanları OpenSearch'e taşır.
 */
@Component
@RequiredArgsConstructor
public class AuditContextMdcFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            applyAuditContext(request);
            filterChain.doFilter(request, response);
        } finally {
            clearAuditContext();
        }
    }

    private void applyAuditContext(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();

        String actionType = resolveActionType(path, method);
        put(AuditContextMdcKeys.ACTION_TYPE, actionType);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                userRepository.findByKeycloakUserId(sub)
                        .ifPresent(u -> put(AuditContextMdcKeys.USER_ID, String.valueOf(u.getId())));
            }
            String preferred = jwt.getClaimAsString("preferred_username");
            if (preferred != null && !preferred.isBlank()) {
                put(AuditContextMdcKeys.USERNAME, preferred);
            }
        }
    }

    private static String resolveActionType(String uri, String method) {
        if (uri == null) return "OTHER";
        if (uri.contains("/api/public/register")) return "REGISTRATION";
        if (uri.contains("/api/admin/")) return "ADMIN";
        if (uri.contains("/api/trades")) return "TRADE";
        if (uri.contains("/api/fund-requests")) return "FUND_REQUEST";
        if (uri.contains("/api/users/me") && ("PATCH".equals(method) || "PUT".equals(method))) return "PROFILE";
        if (uri.contains("/api/users/")) return "USER_ADMIN";
        if (uri.contains("/api/portfolio")) return "PORTFOLIO";
        if (uri.contains("/api/tasks")) return "RISK_OPS";
        if (uri.contains("/api/timeline")) return "TIMELINE";
        if (uri.contains("/api/balance")) return "BALANCE";
        if (uri.contains("/api/transactions")) return "TRANSACTION";
        if (uri.contains("/api/market/terminal")) return "MARKET";
        if (uri.startsWith("/api/")) return "API";
        return "OTHER";
    }

    private static void put(String key, String value) {
        if (value == null || value.isBlank()) return;
        MDC.put(key, value);
        ThreadContext.put(key, value);
    }

    private static void clearAuditContext() {
        MDC.remove(AuditContextMdcKeys.USER_ID);
        MDC.remove(AuditContextMdcKeys.ACTION_TYPE);
        MDC.remove(AuditContextMdcKeys.USERNAME);
        ThreadContext.remove(AuditContextMdcKeys.USER_ID);
        ThreadContext.remove(AuditContextMdcKeys.ACTION_TYPE);
        ThreadContext.remove(AuditContextMdcKeys.USERNAME);
    }
}
