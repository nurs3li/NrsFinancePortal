package com.nurseli.notificationservice.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP isteklerine {@code X-Correlation-Id} başlığı ekler; MDC ve Log4j {@code ThreadContext}'e taşır.
 */
@Slf4j
@Component
@Order(0)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * {@code doFilter} — Gelen istekte correlation id çözümler veya üretir; yanıt başlığına ve log context'e yazar.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpRequest) {
                String correlationId = resolveCorrelationId(httpRequest);
                MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
                ThreadContext.put(CORRELATION_ID_MDC_KEY, correlationId);
                if (response instanceof HttpServletResponse httpResponse) {
                    httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
                }
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
            ThreadContext.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    /**
     * {@code resolveCorrelationId} — İstek başlığındaki id'yi kullanır; yoksa yeni UUID üretir.
     */
    private static String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }
}
