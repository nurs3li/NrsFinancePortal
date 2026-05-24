package com.nurseli.logconsumer.api.response;

import com.nurseli.logconsumer.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.ThreadContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API hata cevapları için standart alan sözlüğü oluşturur; correlationId ve audit izlenebilirliği sağlar.
 */
public final class ApiErrorBody {

    private ApiErrorBody() {}

    /**
     * {@code of} — Hata kodu, mesaj, zaman damgası, path ve correlationId içeren hata gövdesi üretir.
     */
    public static Map<String, Object> of(String code, String message, HttpServletRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("message", message);
        map.put("timestamp", Instant.now().toString());
        map.put("error", message);
        map.put("path", request != null ? request.getRequestURI() : null);
        map.put("correlationId", resolveCorrelationId(request));
        return map;
    }

    private static String resolveCorrelationId(HttpServletRequest request) {
        String fromMdc = ThreadContext.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        if (request != null) {
            String header = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            if (header != null && !header.isBlank()) {
                return header.trim();
            }
        }
        return null;
    }
}
