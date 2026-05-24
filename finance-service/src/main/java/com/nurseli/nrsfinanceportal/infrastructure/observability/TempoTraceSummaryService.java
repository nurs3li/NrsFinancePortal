package com.nurseli.nrsfinanceportal.infrastructure.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.config.ObservabilityProperties;
import com.nurseli.nrsfinanceportal.infrastructure.observability.dto.TraceServiceEdgeDto;
import com.nurseli.nrsfinanceportal.infrastructure.observability.dto.TraceServiceNodeDto;
import com.nurseli.nrsfinanceportal.infrastructure.observability.dto.TraceSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grafana Tempo trace API ile servis düğüm ve kenar özetleri üretir.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TempoTraceSummaryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObservabilityProperties observabilityProperties;
    @Qualifier("tempoHttp")
    private final RestClient tempoRestClient;

    /**
     * traceId için Tempo'dan span'leri okuyup servis düğüm ve kenar özetini üretir.
     */
    public TraceSummaryResponse summarize(String traceIdRaw) {
        if (!observabilityProperties.isEnabled()) {
            return TraceSummaryResponse.disabled("app.observability.enabled=false");
        }
        String traceId = normalizeTraceId(traceIdRaw);
        if (traceId.isBlank()) {
            return TraceSummaryResponse.notFound("");
        }
        try {
            String path = "/api/traces/" + traceId;
            String body = tempoRestClient.get()
                    .uri(path)
                    .retrieve()
                    .body(String.class);
            JsonNode root = MAPPER.readTree(body);
            SpanAccumulator acc = new SpanAccumulator();
            collectSpans(root, acc);
            if (acc.spanIdToService.isEmpty()) {
                return TraceSummaryResponse.notFound(traceId);
            }
            List<TraceServiceNodeDto> nodes = buildNodes(acc);
            List<TraceServiceEdgeDto> edges = buildEdges(acc);
            return new TraceSummaryResponse(true, null, true, traceId, nodes, edges, null);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return TraceSummaryResponse.notFound(traceId);
            }
            log.warn("[TEMPO] summarize failed status={}", e.getStatusCode());
            return new TraceSummaryResponse(true, null, false, traceId, List.of(), List.of(), "Tempo error: " + e.getStatusCode());
        } catch (Exception e) {
            log.warn("[TEMPO] summarize failed: {}", e.getMessage());
            return new TraceSummaryResponse(true, null, false, traceId, List.of(), List.of(), "Tempo unavailable");
        }
    }

    private static String normalizeTraceId(String raw) {
        if (raw == null) return "";
        String t = raw.trim().replace("-", "").toLowerCase();
        if (t.length() > 32) {
            t = t.substring(0, 32);
        }
        return t;
    }

    private void collectSpans(JsonNode node, SpanAccumulator acc) {
        if (node == null || node.isMissingNode()) return;
        if (node.has("resourceSpans")) {
            for (JsonNode rs : node.get("resourceSpans")) {
                ingestResourceSpanBlock(rs, acc);
            }
        }
        if (node.has("batches")) {
            for (JsonNode batch : node.get("batches")) {
                String service = serviceName(batch.path("resource"));
                if (batch.has("scopeSpans")) {
                    for (JsonNode ss : batch.get("scopeSpans")) {
                        ingestScopeSpans(ss, service, acc);
                    }
                }
                if (batch.has("instrumentationLibrarySpans")) {
                    for (JsonNode ils : batch.get("instrumentationLibrarySpans")) {
                        ingestScopeSpans(ils, service, acc);
                    }
                }
            }
        }
        if (node.has("trace")) {
            collectSpans(node.get("trace"), acc);
        }
    }

    private void ingestResourceSpanBlock(JsonNode rs, SpanAccumulator acc) {
        String service = serviceName(rs.path("resource"));
        if (rs.has("scopeSpans")) {
            for (JsonNode ss : rs.get("scopeSpans")) {
                ingestScopeSpans(ss, service, acc);
            }
        }
        if (rs.has("instrumentationLibrarySpans")) {
            for (JsonNode ils : rs.get("instrumentationLibrarySpans")) {
                ingestScopeSpans(ils, service, acc);
            }
        }
    }

    private void ingestScopeSpans(JsonNode ss, String service, SpanAccumulator acc) {
        if (!ss.has("spans")) return;
        for (JsonNode sp : ss.get("spans")) {
            acc.ingestSpan(sp, service);
        }
    }

    private static String serviceName(JsonNode resource) {
        if (resource == null || !resource.has("attributes")) return "unknown";
        for (JsonNode attr : resource.get("attributes")) {
            if (!"service.name".equals(attr.path("key").asText())) continue;
            JsonNode v = attr.path("value");
            if (v.has("stringValue")) return v.get("stringValue").asText("unknown");
            if (v.has("string_value")) return v.get("string_value").asText("unknown");
        }
        return "unknown";
    }

    private List<TraceServiceNodeDto> buildNodes(SpanAccumulator acc) {
        Map<String, Double> sumMs = new HashMap<>();
        for (Map.Entry<String, String> e : acc.spanIdToService.entrySet()) {
            double d = acc.spanIdToDurationMs.getOrDefault(e.getKey(), 0.0);
            sumMs.merge(e.getValue(), d, Double::sum);
        }
        List<TraceServiceNodeDto> out = new ArrayList<>();
        sumMs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(en -> out.add(new TraceServiceNodeDto(en.getKey(), en.getKey(), round2(en.getValue()))));
        return out;
    }

    private List<TraceServiceEdgeDto> buildEdges(SpanAccumulator acc) {
        Set<String> seen = new HashSet<>();
        List<TraceServiceEdgeDto> out = new ArrayList<>();
        for (Map.Entry<String, String> e : acc.spanIdToParent.entrySet()) {
            String childSpan = e.getKey();
            String parentSpan = e.getValue();
            if (parentSpan == null || isZeroId(parentSpan)) continue;
            String fromSvc = acc.spanIdToService.get(parentSpan);
            String toSvc = acc.spanIdToService.get(childSpan);
            if (fromSvc == null || toSvc == null) continue;
            if (fromSvc.equals(toSvc)) continue;
            double d = acc.spanIdToDurationMs.getOrDefault(childSpan, 0.0);
            String key = fromSvc + "->" + toSvc;
            if (seen.add(key)) {
                out.add(new TraceServiceEdgeDto(fromSvc, toSvc, round2(d)));
            }
        }
        out.sort(Comparator.comparing(TraceServiceEdgeDto::from).thenComparing(TraceServiceEdgeDto::to));
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static boolean isZeroId(String id) {
        if (id == null || id.isBlank()) return true;
        String t = id.replace("0", "").replace("=", "").trim();
        return t.isEmpty();
    }

    private static final class SpanAccumulator {
        final Map<String, String> spanIdToService = new LinkedHashMap<>();
        final Map<String, Double> spanIdToDurationMs = new HashMap<>();
        final Map<String, String> spanIdToParent = new HashMap<>();

        void ingestSpan(JsonNode span, String fallbackService) {
            String spanId = idField(span, "spanId", "span_id");
            if (spanId == null) return;
            String parent = idField(span, "parentSpanId", "parent_span_id");
            long start = nano(span.path("startTimeUnixNano"));
            long end = nano(span.path("endTimeUnixNano"));
            double ms = (end > start) ? (end - start) / 1_000_000.0 : 0.0;
            spanIdToService.put(spanId, fallbackService);
            spanIdToDurationMs.put(spanId, ms);
            if (parent != null) {
                spanIdToParent.put(spanId, parent);
            }
        }

        private static String idField(JsonNode span, String camel, String snake) {
            String v = text(span, camel);
            if (v == null) v = text(span, snake);
            return v == null ? null : v.trim();
        }

        private static String text(JsonNode n, String f) {
            JsonNode x = n.path(f);
            if (x.isMissingNode() || x.isNull()) return null;
            if (x.isTextual()) return x.asText();
            if (x.isBinary()) {
                try {
                    byte[] b = x.binaryValue();
                    return b != null ? bytesToHex(b) : null;
                } catch (IOException e) {
                    return null;
                }
            }
            return x.asText(null);
        }

        private static long nano(JsonNode n) {
            if (n.isMissingNode() || n.isNull()) return 0L;
            if (n.isNumber()) return n.asLong();
            try {
                return Long.parseLong(n.asText("0"));
            } catch (Exception e) {
                return 0L;
            }
        }

        private static String bytesToHex(byte[] b) {
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        }
    }
}
