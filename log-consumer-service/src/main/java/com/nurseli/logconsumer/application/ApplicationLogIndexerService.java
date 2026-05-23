package com.nurseli.logconsumer.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code application-logs} Kafka topic'inden gelen JSON log kayıtlarını OpenSearch'e index eder.
 * Günlük index adı: {@code application-logs-{yyyy-MM-dd}}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationLogIndexerService {

    public static final String INDEX_PREFIX = "application-logs";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestHighLevelClient opensearchClient;

    /**
     * {@code indexLog} — Ham JSON payload'ı parse eder, alanları normalize eder ve OpenSearch'e yazar.
     */
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
            source.put("traceId", getText(node, "traceId"));
            source.put("spanId", getText(node, "spanId"));
            putIfPresent(source, "userId", getText(node, "userId"));
            putIfPresent(source, "actionType", getText(node, "actionType"));
            putIfPresent(source, "username", getText(node, "username"));
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

    /**
     * {@code resolveIndexName} — Log zaman damgasına göre günlük index adını üretir; parse edilemezse bugünü kullanır.
     */
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

    /**
     * {@code getText} — JSON node içinden string alan okur; yoksa veya null ise {@code null} döner.
     */
    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? null : f.asText();
    }

    /**
     * {@code putIfPresent} — Boş olmayan değerleri OpenSearch kaynak map'ine ekler.
     */
    private static void putIfPresent(Map<String, Object> source, String key, String value) {
        if (value != null && !value.isBlank()) {
            source.put(key, value);
        }
    }

    /**
     * {@code normalizeTimestamp} — Log4j {@code MutableInstant[...]} formatını ISO-8601 string'e çevirir.
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
