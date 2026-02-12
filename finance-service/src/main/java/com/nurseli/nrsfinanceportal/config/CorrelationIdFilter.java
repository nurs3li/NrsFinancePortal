package com.nurseli.nrsfinanceportal.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Her HTTP request için bir correlationId üretir / okur ve:
 * - MDC'ye koyar (log pattern'inde %X{correlationId} ile görülebilir)
 * - Sonraki katmanlar (servisler, Kafka publisher vs.) bu MDC üzerinden erişebilir.
 *
 * Header ismi: X-Correlation-Id
 */
@Slf4j
@Component
@Order(0)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        try {
            if (request instanceof HttpServletRequest httpRequest) {
                String incoming = httpRequest.getHeader(CORRELATION_ID_HEADER);

                String correlationId =
                        (incoming != null && !incoming.isBlank())
                                ? incoming
                                : UUID.randomUUID().toString();

                // MDC'ye koy
                MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            }

            chain.doFilter(request, response);

        } finally {
            // Thread reuse nedeniyle sonunda temizlemek önemli
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}