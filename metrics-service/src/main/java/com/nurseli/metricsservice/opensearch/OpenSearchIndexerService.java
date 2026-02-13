package com.nurseli.metricsservice.opensearch;

import com.nurseli.metricsservice.event.SuspiciousActivityDetectedEvent;
import com.nurseli.metricsservice.event.TradeCreatedEvent;
import com.nurseli.metricsservice.event.WhaleAlertDetectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenSearchIndexerService {

    public static final String INDEX_TRADES = "metrics-trades";
    public static final String INDEX_WHALES = "metrics-whales";
    public static final String INDEX_SUSPICIOUS = "metrics-suspicious";

    private final RestHighLevelClient opensearchClient;

    public void indexTrade(TradeCreatedEvent event) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("tradeId", event.tradeId());
        doc.put("userId", event.userId());
        doc.put("tradeType", event.tradeType());
        doc.put("assetType", event.assetType());
        doc.put("symbol", event.symbol());
        doc.put("quantity", event.quantity() != null ? event.quantity().doubleValue() : null);
        doc.put("pricePerUnit", event.pricePerUnit() != null ? event.pricePerUnit().doubleValue() : null);
        doc.put("totalTry", event.totalTry() != null ? event.totalTry().doubleValue() : null);
        doc.put("occurredAt", event.occurredAt() != null ? event.occurredAt().toString() : null);
        index(INDEX_TRADES, "trade-" + event.tradeId(), doc);
    }

    public void indexWhale(WhaleAlertDetectedEvent event) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("userId", event.userId());
        doc.put("whaleLevel", event.whaleLevel());
        doc.put("impactScore", event.impactScore());
        doc.put("dailyVolume", event.dailyVolume() != null ? event.dailyVolume().doubleValue() : null);
        doc.put("hourlyTransactionCount", event.hourlyTransactionCount());
        doc.put("maxSingleTransaction", event.maxSingleTransaction() != null ? event.maxSingleTransaction().doubleValue() : null);
        doc.put("trendDirection", event.trendDirection());
        doc.put("triggeredAt", event.triggeredAt() != null ? event.triggeredAt().toString() : null);
        String id = "whale-" + event.userId() + "-" + (event.triggeredAt() != null ? event.triggeredAt().toEpochMilli() : System.currentTimeMillis());
        index(INDEX_WHALES, id, doc);
    }

    public void indexSuspicious(SuspiciousActivityDetectedEvent event) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("userId", event.userId());
        doc.put("transactionId", event.transactionId());
        doc.put("reason", event.reason());
        doc.put("amount", event.amount() != null ? event.amount().doubleValue() : null);
        doc.put("countInWindow", event.countInWindow());
        doc.put("thresholdAmount", event.thresholdAmount() != null ? event.thresholdAmount().doubleValue() : null);
        doc.put("thresholdCount", event.thresholdCount());
        doc.put("occurredAt", event.occurredAt() != null ? event.occurredAt().toString() : null);
        String id = "susp-" + event.userId() + "-" + event.transactionId() + "-" + (event.occurredAt() != null ? event.occurredAt().toEpochMilli() : System.currentTimeMillis());
        index(INDEX_SUSPICIOUS, id, doc);
    }

    private void index(String indexName, String id, Map<String, Object> source) {
        try {
            IndexRequest request = new IndexRequest(indexName).id(id).source(source);
            opensearchClient.index(request, RequestOptions.DEFAULT);
            log.debug("[OPENSEARCH] indexed index={} id={}", indexName, id);
        } catch (IOException e) {
            log.error("[OPENSEARCH] index failed index={} id={}", indexName, id, e);
        }
    }
}