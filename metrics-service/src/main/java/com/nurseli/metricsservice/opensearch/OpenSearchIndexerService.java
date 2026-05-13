package com.nurseli.metricsservice.opensearch;

import com.nurseli.metricsservice.event.InvestorBehaviorUpdatedEvent;
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
    public static final String INDEX_INVESTOR_BEHAVIOR = "investor-behavior-events";

    private final RestHighLevelClient opensearchClient;

    public void indexTrade(TradeCreatedEvent event) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("tradeId", event.tradeId());
        doc.put("userId", event.userId());
        doc.put("tradeType", event.tradeType());
        doc.put("assetType", event.assetType());
        doc.put("symbol", normalizeSymbol(event.assetType(), event.symbol()));
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

    public void indexInvestorBehavior(InvestorBehaviorUpdatedEvent event) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("eventId", event.eventId());
        doc.put("occurredAt", event.occurredAt() != null ? event.occurredAt().toString() : null);
        doc.put("userId", event.userId());
        doc.put("investorLevel", event.investorLevel());
        doc.put("portfolioImpactScore", event.portfolioImpactScore());
        doc.put("totalPortfolioValueTry", bd(event.totalPortfolioValueTry()));
        doc.put("totalInvestedAmountTry", bd(event.totalInvestedAmountTry()));
        doc.put("totalNominalProfitTry", bd(event.totalNominalProfitTry()));
        doc.put("totalRealProfitTry", bd(event.totalRealProfitTry()));
        doc.put("largestPositionSymbol", event.largestPositionSymbol());
        doc.put("largestPositionValueTry", bd(event.largestPositionValueTry()));
        doc.put("largestPositionRatio", bd(event.largestPositionRatio()));
        doc.put("assetConcentrationScore", event.assetConcentrationScore());
        doc.put("profitScore", event.profitScore());
        doc.put("realProfitScore", event.realProfitScore());
        doc.put("riskExposureScore", event.riskExposureScore());
        doc.put("positionCount", event.positionCount());
        doc.put("openPositionCount", event.openPositionCount());
        doc.put("closedPositionCount", event.closedPositionCount());
        doc.put("cryptoExposureRatio", bd(event.cryptoExposureRatio()));
        doc.put("equityExposureRatio", bd(event.equityExposureRatio()));
        doc.put("fxExposureRatio", bd(event.fxExposureRatio()));
        doc.put("fundExposureRatio", bd(event.fundExposureRatio()));
        doc.put("metalExposureRatio", bd(event.metalExposureRatio()));
        doc.put("explanationMessages", event.explanationMessages());
        String id = "inv-" + event.userId() + "-" + (event.eventId() != null ? event.eventId() : String.valueOf(System.currentTimeMillis()));
        index(INDEX_INVESTOR_BEHAVIOR, id, doc);
    }

    private static Double bd(java.math.BigDecimal v) {
        return v != null ? v.doubleValue() : null;
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
    /**
     * CRYPTO için BTC -> BTCUSDT; diğer tiplerde aynen.
     */
    private static String normalizeSymbol(String assetType, String symbol) {
        if (symbol == null || symbol.isBlank()) return symbol;
        if ("CRYPTO".equalsIgnoreCase(assetType) && !symbol.toUpperCase().endsWith("USDT")) {
            return symbol + "USDT";
        }
        return symbol;
    }
}