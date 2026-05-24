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
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis tabanlı istek hız sınırlama filter'ı; /api/* isteklerini IP başına dakikada N ile sınırlar.
 * Limit aşıldığında 429 Too Many Requests döner.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:ip:";
    private static final Set<String> DASHBOARD_READ_PATHS = Set.of(
            "/api/users/me",
            "/api/me/starred-assets"
    );

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    @Override
    /**
 * IP başına Redis sayaç ile rate limit uygular; dashboard read path'leri muaf tutulabilir.
 */
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

        // 1) Dashboard/oturum için kritik GET endpoint'lerini rate-limit dışı bırak
        if ("GET".equals(method) && (path.equals("/api/dashboard/summary") || DASHBOARD_READ_PATHS.contains(path))) {
            chain.doFilter(request, response);
            return;
        }

        // 1b) Portföy snapshot / manuel zaman serisi okuma — dashboard yüklemede 429 önlemek için GET muaf
        if ("GET".equals(method) && (path.startsWith("/api/portfolio/snapshots") || path.startsWith("/api/portfolio/manual/timeseries"))) {
            chain.doFilter(request, response);
            return;
        }

        // 1c) Portföy AI — uzun süren analiz + test sırasında Redis IP limiti 429 üretmesin
        if (path.startsWith("/api/portfolio/ai/")) {
            chain.doFilter(request, response);
            return;
        }

        // 2) /api dışındaki istekleri zaten sınırlamıyoruz
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);
        // Tek IP altında farklı endpointlerin birbirini kilitlemesini engellemek için sayaç anahtarını endpoint bazlı tut.
        String endpointKey = method + ":" + path;
        String key = RATE_LIMIT_KEY_PREFIX + clientIp + ":" + endpointKey;

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