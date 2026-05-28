package com.nurseli.logconsumer.infrastructure.opensearch;

import com.nurseli.logconsumer.application.ApplicationLogIndexerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Uygulama ayağa kalkınca günlük {@code application-logs-{date}} OpenSearch index'ini ve field mapping'ini oluşturur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationLogsIndexInitializer {

    private final RestHighLevelClient opensearchClient;

    /**
     * {@code ensureIndex} — Bugünün index adını hesaplar; yoksa mapping ile birlikte oluşturur.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndex() {
        String indexName = ApplicationLogIndexerService.INDEX_PREFIX + "-"
                + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        ensureIndex(indexName, applicationLogsMapping());
    }

    /**
     * {@code ensureIndex} — Verilen index adının varlığını kontrol eder; yoksa mapping ile oluşturur.
     */
    private void ensureIndex(String indexName, Map<String, Object> mapping) {
        try {
            GetIndexRequest getRequest = new GetIndexRequest(indexName);
            boolean exists = opensearchClient.indices().exists(getRequest, RequestOptions.DEFAULT);
            if (exists) {
                log.info("[OPENSEARCH] index already exists: {}", indexName);
                return;
            }
            CreateIndexRequest createRequest = new CreateIndexRequest(indexName).mapping(mapping);
            opensearchClient.indices().create(createRequest, RequestOptions.DEFAULT);
            log.info("[OPENSEARCH] index created: {}", indexName);
        } catch (IOException e) {
            log.warn("[OPENSEARCH] could not ensure index {}: {}", indexName, e.getMessage());
        }
    }

    /**
     * {@code applicationLogsMapping} — Log alanları için OpenSearch index mapping tanımını döner.
     */
    private Map<String, Object> applicationLogsMapping() {
        Map<String, Object> props = new HashMap<>();
        props.put("timestamp", Map.of("type", "date", "format", "strict_date_optional_time||epoch_millis"));
        props.put("level", Map.of("type", "keyword"));
        props.put("serviceName", Map.of("type", "keyword"));
        props.put("message", Map.of("type", "text"));
        props.put("logger", Map.of("type", "keyword"));
        props.put("thread", Map.of("type", "keyword"));
        props.put("correlationId", Map.of("type", "keyword"));
        props.put("traceId", Map.of("type", "keyword"));
        props.put("spanId", Map.of("type", "keyword"));
        props.put("userId", Map.of("type", "keyword"));
        props.put("username", Map.of("type", "keyword"));
        props.put("actionType", Map.of("type", "keyword"));
        props.put("exception", Map.of("type", "keyword"));
        props.put("stackTrace", Map.of("type", "text"));
        return Map.of("properties", props);
    }
}
