package com.nurseli.metricsservice.opensearch;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Uygulama ayağa kalktığında OpenSearch index'lerini oluşturur.
 * symbol, whaleLevel keyword → aggregation'lar çalışır. Map ile XContentBuilder çakışması yok.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSearchIndexInitializer {

    private final RestHighLevelClient opensearchClient;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndices() {
        ensureIndex(OpenSearchIndexerService.INDEX_TRADES, tradesMapping());
        ensureIndex(OpenSearchIndexerService.INDEX_WHALES, whalesMapping());
        ensureIndex(OpenSearchIndexerService.INDEX_SUSPICIOUS, suspiciousMapping());
    }

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

    private Map<String, Object> tradesMapping() {
        Map<String, Object> props = new HashMap<>();
        props.put("tradeId", Map.of("type", "long"));
        props.put("userId", Map.of("type", "long"));
        props.put("tradeType", Map.of("type", "keyword"));
        props.put("assetType", Map.of("type", "keyword"));
        props.put("symbol", Map.of("type", "keyword"));
        props.put("quantity", Map.of("type", "double"));
        props.put("pricePerUnit", Map.of("type", "double"));
        props.put("totalTry", Map.of("type", "double"));
        props.put("occurredAt", Map.of("type", "date", "format", "strict_date_optional_time||epoch_millis"));
        return Map.of("properties", props);
    }

    private Map<String, Object> whalesMapping() {
        Map<String, Object> props = new HashMap<>();
        props.put("userId", Map.of("type", "long"));
        props.put("whaleLevel", Map.of("type", "keyword"));
        props.put("impactScore", Map.of("type", "integer"));
        props.put("dailyVolume", Map.of("type", "double"));
        props.put("hourlyTransactionCount", Map.of("type", "integer"));
        props.put("maxSingleTransaction", Map.of("type", "double"));
        props.put("trendDirection", Map.of("type", "keyword"));
        props.put("triggeredAt", Map.of("type", "date", "format", "strict_date_optional_time||epoch_millis"));
        return Map.of("properties", props);
    }

    private Map<String, Object> suspiciousMapping() {
        Map<String, Object> props = new HashMap<>();
        props.put("userId", Map.of("type", "long"));
        props.put("transactionId", Map.of("type", "long"));
        props.put("reason", Map.of("type", "keyword"));
        props.put("amount", Map.of("type", "double"));
        props.put("countInWindow", Map.of("type", "long"));
        props.put("thresholdAmount", Map.of("type", "double"));
        props.put("thresholdCount", Map.of("type", "integer"));
        props.put("occurredAt", Map.of("type", "date", "format", "strict_date_optional_time||epoch_millis"));
        return Map.of("properties", props);
    }
}