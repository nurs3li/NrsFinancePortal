package com.nurseli.nrsfinanceportal.infrastructure.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.config.ObservabilityProperties;
import com.nurseli.nrsfinanceportal.infrastructure.observability.dto.AuditLogDetailResponse;
import com.nurseli.nrsfinanceportal.infrastructure.observability.dto.AuditLogPageResponse;
import com.nurseli.nrsfinanceportal.infrastructure.observability.dto.AuditLogRowDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenSearch üzerinden admin audit log arama ve detay okuma.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OpenSearchAuditLogService {

    /** Admin audit listesinde gösterilecek / sayfalanacak en fazla kayıt (performans + UI). */
    public static final int MAX_AUDIT_HITS = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObservabilityProperties observabilityProperties;
    @Qualifier("opensearchHttp")
    private final RestClient openSearchRestClient;

    /**
     * OpenSearch'te filtreli audit log araması yapar; sayfalanmış AuditLogPageResponse döner.
     */
    public AuditLogPageResponse search(
            String fromIso,
            String toIso,
            int page,
            int size,
            String serviceName,
            String level,
            String levelsCsv,
            String traceId,
            String correlationId,
            String userId,
            String actionType,
            String username,
            String q
    ) {
        if (!observabilityProperties.isEnabled()) {
            return AuditLogPageResponse.disabled("app.observability.enabled=false");
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        int from = safePage * safeSize;
        if (from >= MAX_AUDIT_HITS) {
            return new AuditLogPageResponse(true, null, Collections.emptyList(), MAX_AUDIT_HITS, safePage, safeSize);
        }
        int effectiveSize = Math.min(safeSize, MAX_AUDIT_HITS - from);

        try {
            String indexPattern = observabilityProperties.getOpenSearch().getIndexPattern();
            String path = "/" + indexPattern + "/_search";

            Map<String, Object> body = buildSearchBody(
                    fromIso, toIso, from, effectiveSize, serviceName, level, levelsCsv,
                    traceId, correlationId, userId, actionType, username, q);
            String json = MAPPER.writeValueAsString(body);

            String raw = openSearchRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(raw);
            JsonNode hits = root.path("hits").path("hits");
            JsonNode totalNode = root.path("hits").path("total");
            long rawTotal = totalNode.isObject() ? totalNode.path("value").asLong(0) : totalNode.asLong(0);
            long total = Math.min(rawTotal, MAX_AUDIT_HITS);

            List<AuditLogRowDto> rows = new ArrayList<>();
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    String index = hit.path("_index").asText(null);
                    String id = hit.path("_id").asText(null);
                    JsonNode src = hit.path("_source");
                    if (index == null || id == null) continue;
                    String cursor = encodeCursor(index, id);
                    rows.add(new AuditLogRowDto(
                            cursor,
                            text(src, "timestamp"),
                            text(src, "level"),
                            text(src, "serviceName"),
                            text(src, "message"),
                            text(src, "traceId"),
                            text(src, "spanId"),
                            text(src, "correlationId"),
                            text(src, "logger")
                    ));
                }
            }
            return new AuditLogPageResponse(true, null, rows, total, safePage, safeSize);
        } catch (RestClientResponseException e) {
            log.warn("[AUDIT-OS] search failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return AuditLogPageResponse.disabled("OpenSearch error: " + e.getStatusCode());
        } catch (Exception e) {
            log.warn("[AUDIT-OS] search failed: {}", e.getMessage());
            return AuditLogPageResponse.disabled("OpenSearch unavailable");
        }
    }

    /**
     * Base64 cursor ile tek audit log _source dokümanını getirir.
     */
    public AuditLogDetailResponse getByCursor(String cursor) {
        if (!observabilityProperties.isEnabled()) {
            return AuditLogDetailResponse.disabled("app.observability.enabled=false");
        }
        String[] parts = decodeCursor(cursor);
        if (parts == null) {
            return new AuditLogDetailResponse(true, "invalid_cursor", Map.of());
        }
        String index = parts[0];
        String id = parts[1];
        try {
            String path = "/" + index + "/_doc/" + id;
            String raw = openSearchRestClient.get()
                    .uri(path)
                    .retrieve()
                    .body(String.class);
            JsonNode root = MAPPER.readTree(raw);
            if (!root.path("found").asBoolean(true)) {
                return new AuditLogDetailResponse(true, "not_found", Map.of());
            }
            JsonNode src = root.path("_source");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.convertValue(src, Map.class);
            return new AuditLogDetailResponse(true, null, map != null ? map : Map.of());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return new AuditLogDetailResponse(true, "not_found", Map.of());
            }
            log.warn("[AUDIT-OS] get doc failed: {}", e.getStatusCode());
            return AuditLogDetailResponse.disabled("OpenSearch error: " + e.getStatusCode());
        } catch (Exception e) {
            log.warn("[AUDIT-OS] get doc failed: {}", e.getMessage());
            return AuditLogDetailResponse.disabled("OpenSearch unavailable");
        }
    }

    private Map<String, Object> buildSearchBody(
            String fromIso,
            String toIso,
            int from,
            int size,
            String serviceName,
            String level,
            String levelsCsv,
            String traceId,
            String correlationId,
            String userId,
            String actionType,
            String username,
            String q
    ) {
        Map<String, Object> query = new HashMap<>();
        List<Map<String, Object>> must = new ArrayList<>();

        if (fromIso != null && !fromIso.isBlank() && toIso != null && !toIso.isBlank()) {
            Map<String, Object> range = Map.of(
                    "timestamp", Map.of(
                            "gte", fromIso,
                            "lte", toIso
                    )
            );
            must.add(Map.of("range", range));
        }

        if (serviceName != null && !serviceName.isBlank()) {
            String raw = serviceName.trim();
            if (raw.contains(",")) {
                List<Map<String, Object>> shouldSvc = new ArrayList<>();
                for (String p : raw.split(",")) {
                    String svc = p.trim();
                    if (!svc.isEmpty()) {
                        shouldSvc.add(Map.of("match_phrase", Map.of("serviceName", svc)));
                    }
                }
                if (!shouldSvc.isEmpty()) {
                    must.add(Map.of("bool", Map.of("should", shouldSvc, "minimum_should_match", 1)));
                }
            } else {
                must.add(Map.of("match_phrase", Map.of("serviceName", raw)));
            }
        }
        java.util.List<String> levelList = parseLevels(levelsCsv, level);
        if (levelList.size() == 1) {
            must.add(Map.of("match_phrase", Map.of("level", levelList.get(0))));
        } else if (levelList.size() > 1) {
            List<Map<String, Object>> shouldLevels = new ArrayList<>();
            for (String lv : levelList) {
                shouldLevels.add(Map.of("match_phrase", Map.of("level", lv)));
            }
            must.add(Map.of("bool", Map.of("should", shouldLevels, "minimum_should_match", 1)));
        }
        if (traceId != null && !traceId.isBlank()) {
            must.add(Map.of("match_phrase", Map.of("traceId", traceId.trim())));
        }
        if (correlationId != null && !correlationId.isBlank()) {
            must.add(Map.of("match_phrase", Map.of("correlationId", correlationId.trim())));
        }
        if (userId != null && !userId.isBlank()) {
            must.add(Map.of("match_phrase", Map.of("userId", userId.trim())));
        }
        if (actionType != null && !actionType.isBlank()) {
            must.add(Map.of("match_phrase", Map.of("actionType", actionType.trim().toUpperCase())));
        }
        if (username != null && !username.isBlank()) {
            String u = username.trim();
            must.add(Map.of("bool", Map.of(
                    "should", List.of(
                            Map.of("match_phrase", Map.of("username", u)),
                            Map.of("match", Map.of("username", Map.of("query", u, "operator", "and")))
                    ),
                    "minimum_should_match", 1
            )));
        }
        if (q != null && !q.isBlank()) {
            // simple_query_string Lucene özel karakterlerinde sessizce 0 sonuç veya hata verebiliyor; çok alanlı match daha öngörülebilir.
            must.add(Map.of("multi_match", Map.of(
                    "query", q.trim(),
                    "type", "best_fields",
                    "operator", "and",
                    "fields", List.of("message^2", "logger", "thread", "exception")
            )));
        }

        if (must.isEmpty()) {
            query.put("match_all", Map.of());
        } else {
            query.put("bool", Map.of("must", must));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("from", from);
        body.put("size", size);
        body.put("sort", List.of(Map.of("timestamp", Map.of("order", "desc"))));
        body.put("track_total_hits", MAX_AUDIT_HITS);
        body.put("query", query);
        return body;
    }

    private static List<String> parseLevels(String levelsCsv, String singleLevel) {
        List<String> out = new ArrayList<>();
        if (levelsCsv != null && !levelsCsv.isBlank()) {
            for (String part : levelsCsv.split(",")) {
                String t = part.trim().toUpperCase();
                if (!t.isEmpty() && !out.contains(t)) {
                    out.add(t);
                }
            }
        }
        if (out.isEmpty() && singleLevel != null && !singleLevel.isBlank()) {
            out.add(singleLevel.trim().toUpperCase());
        }
        return out;
    }

    private static String text(JsonNode src, String field) {
        JsonNode n = src.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText(null);
    }

    /**
     * OpenSearch index ve _id değerlerinden sayfalama cursor'u üretir.
     */
    public static String encodeCursor(String index, String id) {
        String raw = index + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String[] decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            byte[] dec = Base64.getUrlDecoder().decode(cursor.trim());
            String s = new String(dec, StandardCharsets.UTF_8);
            int pipe = s.indexOf('|');
            if (pipe <= 0 || pipe >= s.length() - 1) return null;
            return new String[]{s.substring(0, pipe), s.substring(pipe + 1)};
        } catch (Exception e) {
            return null;
        }
    }
}
