package com.nurseli.nrsfinanceportal.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * /api/* isteklerini IP başına dakikada N ile sınırlar.
 * Aşan isteklere 429 Too Many Requests döner.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:ip:";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // 1) Dashboard özet endpoint'ini rate-limit dışı bırak
        if (path.equals("/api/dashboard/summary")) {
            chain.doFilter(request, response);
            return;
        }

        // 1b) Portföy snapshot okuma — sayfa başına çoklu istekte 429 önlemek için GET muaf
        if ("GET".equals(method) && path.startsWith("/api/portfolio/snapshots")) {
            chain.doFilter(request, response);
            return;
        }

        // 2) FM / Admin için sadece "okuma" GET isteklerini rate-limit dışı bırak
        if ("GET".equals(method)
                && (path.startsWith("/api/tasks")                 // /api/tasks/me, /api/tasks/{id}
                || path.startsWith("/api/admin/tasks")        // admin görev listesi/detayı
                || path.startsWith("/api/admin/suspicious")   // şüpheli olay listeleri
                || path.startsWith("/api/admin/accounts"))) { // admin hesap listesi
            chain.doFilter(request, response);
            return;
        }

        // 3) /api dışındaki istekleri zaten sınırlamıyoruz
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) count = 1L;
            if (count == 1) {
                redisTemplate.expire(key, properties.getWindowSec(), TimeUnit.SECONDS);
            }

            if (count > properties.getMaxRequestsPerMinute()) {
                log.warn("[RATE_LIMIT] Rejected ip={} path={} count={} max={}",
                        clientIp, path, count, properties.getMaxRequestsPerMinute());
                httpResponse.setStatus(429);  // Too Many Requests
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Too many requests. Try again later.\"}");
                return;
            }
        } catch (Exception e) {
            log.error("[RATE_LIMIT] Redis error, allowing request", e);
            // Redis down ise isteği geçir
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isBlank()) {
            return xForwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}