package com.nurseli.whaleanalytics.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * FAZ 1.1.6 – Her HTTP isteği için: method, URI, status, duration loglar.
 */
@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LogManager.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = httpResponse.getStatus();
            String method = httpRequest.getMethod();
            String uri = httpRequest.getRequestURI();
            String query = httpRequest.getQueryString();
            String fullUri = query != null ? uri + "?" + query : uri;

            log.info("[REQUEST] {} {} status={} durationMs={}", method, fullUri, status, duration);
        }
    }
}
