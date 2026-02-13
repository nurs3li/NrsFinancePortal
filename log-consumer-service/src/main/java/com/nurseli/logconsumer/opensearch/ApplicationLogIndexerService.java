
package com.nurseli.logconsumer.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * application-logs topic'inden gelen JSON logları OpenSearch'e index eder.
 * Index: application-logs-{yyyy-MM-dd}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationLogIndexerService {

    public static final String INDEX_PREFIX = "application-logs";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestHighLevelClient opensearchClient;

    public void indexLog(String jsonPayload) {
        try {
            JsonNode node = MAPPER.readTree(jsonPayload);
            String indexName = resolveIndexName(node);
            String docId = UUID.randomUUID().toString();

            Map<String, Object> source = new HashMap<>();
            source.put("timestamp", normalizeTimestamp(getText(node, "timestamp")));
            source.put("level", getText(node, "level"));
            source.put("serviceName", getText(node, "serviceName"));
            source.put("message", getText(node, "message"));
            source.put("logger", getText(node, "logger"));
            source.put("thread", getText(node, "thread"));
            source.put("correlationId", getText(node, "correlationId"));
            source.put("exception", getText(node, "exception"));
            if (node.has("stackTrace")) {
                source.put("stackTrace", node.get("stackTrace").toString());
            }

            IndexRequest request = new IndexRequest(indexName).id(docId).source(source);
            opensearchClient.index(request, RequestOptions.DEFAULT);
            log.debug("[OPENSEARCH] indexed log index={} level={}", indexName, source.get("level"));
        } catch (Exception e) {
            log.error("[OPENSEARCH] failed to index log: {}", e.getMessage());
        }
    }

    private String resolveIndexName(JsonNode node) {
        String ts = getText(node, "timestamp");
        if (ts != null && !ts.isBlank()) {
            try {
                Instant instant = Instant.parse(ts);
                LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
                return INDEX_PREFIX + "-" + date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ignored) {
            }
        }
        return INDEX_PREFIX + "-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? null : f.asText();
    }

    /**
     * MutableInstant[epochSecond=..., nano=...] formatını ISO-8601'e çevirir.
     * OpenSearch date alanı sadece ISO veya epoch millis kabul eder.
     */
    private String normalizeTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.startsWith("MutableInstant[")) {
            try {
                int secStart = raw.indexOf("epochSecond=") + 12;
                int secEnd = raw.indexOf(",", secStart);
                int nanoStart = raw.indexOf("nano=") + 5;
                int nanoEnd = raw.indexOf("]", nanoStart);
                long epochSecond = Long.parseLong(raw.substring(secStart, secEnd).trim());
                int nano = Integer.parseInt(raw.substring(nanoStart, nanoEnd).trim());
                long epochMillis = epochSecond * 1000L + nano / 1_000_000;
                return Instant.ofEpochMilli(epochMillis).toString();
            } catch (Exception e) {
                return Instant.now().toString();
            }
        }
        return raw;
    }
}