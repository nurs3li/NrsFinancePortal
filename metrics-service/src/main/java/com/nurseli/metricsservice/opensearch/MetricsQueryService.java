package com.nurseli.metricsservice.opensearch;

import com.nurseli.metricsservice.api.dto.DashboardMetricsDto;
import com.nurseli.metricsservice.opensearch.OpenSearchIndexerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsQueryService {

    private final RestHighLevelClient opensearchClient;

    public long countTrades() throws IOException {
        SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_TRADES);
        request.source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(0));
        SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
        return getTotalHitCount(response);
    }

    public long countWhales() throws IOException {
        SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_WHALES);
        request.source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(0));
        SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
        return getTotalHitCount(response);
    }

    public long countSuspicious() throws IOException {
        SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_SUSPICIOUS);
        request.source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(0));
        SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
        return getTotalHitCount(response);
    }

    private long getTotalHitCount(SearchResponse response) {
        if (response == null || response.getHits() == null) {
            return 0L;
        }
        var totalHits = response.getHits().getTotalHits();
        if (totalHits == null) {
            return 0L;
        }
        return totalHits.value;
    }

    public DashboardMetricsDto getDashboard() {
        try {
            long trades = countTrades();
            long whales = countWhales();
            long suspicious = countSuspicious();

            List<DashboardMetricsDto.TradeCountBySymbol> topSymbols = getTopSymbols(10);
            List<DashboardMetricsDto.WhaleCountByLevel> whaleByLevel = getWhaleByLevel();

            long investorEvents = countInvestorBehaviorSafe();
            List<DashboardMetricsDto.WhaleCountByLevel> investorByLevel = getInvestorByLevelSafe();
            double avgImpact = avgInvestorImpactScoreSafe();
            double maxImpact = maxInvestorImpactScoreSafe();
            double maxPortfolioVal = maxInvestorTotalPortfolioValueSafe();

            return new DashboardMetricsDto(
                    trades,
                    whales,
                    suspicious,
                    topSymbols,
                    whaleByLevel,
                    investorEvents,
                    investorByLevel,
                    avgImpact,
                    maxImpact,
                    maxPortfolioVal
            );
        } catch (IOException e) {
            log.error("[METRICS] dashboard query failed", e);
            return new DashboardMetricsDto(0, 0, 0, List.of(), List.of(), 0, List.of(), 0, 0, 0);
        }
    }

    private List<DashboardMetricsDto.TradeCountBySymbol> getTopSymbols(int size) throws IOException {
        SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_TRADES);
        SearchSourceBuilder src = new SearchSourceBuilder().size(0);
        src.aggregation(AggregationBuilders.terms("by_symbol").field("symbol").size(50));
        request.source(src);

        SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
        if (response.getAggregations() == null) return List.of();
        Terms terms = response.getAggregations().get("by_symbol");
        if (terms == null) return List.of();

        // BTC ve BTCUSDT gibi aynı varlığı birleştir
        java.util.Map<String, Long> merged = new java.util.HashMap<>();
        for (Terms.Bucket b : terms.getBuckets()) {
            String sym = b.getKeyAsString();
            String normalized = (sym != null && !sym.toUpperCase().endsWith("USDT"))
                    ? sym + "USDT"
                    : sym;
            merged.merge(normalized, b.getDocCount(), Long::sum);
        }

        return merged.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(size)
                .map(e -> new DashboardMetricsDto.TradeCountBySymbol(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private List<DashboardMetricsDto.WhaleCountByLevel> getWhaleByLevel() throws IOException {
        SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_WHALES);
        SearchSourceBuilder src = new SearchSourceBuilder().size(0);
        src.aggregation(
                AggregationBuilders.terms("by_level").field("whaleLevel").size(10)
                        .subAggregation(AggregationBuilders.cardinality("unique_users").field("userId"))
        );
        request.source(src);

        SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
        if (response.getAggregations() == null) return List.of();
        Terms terms = response.getAggregations().get("by_level");
        if (terms == null) return List.of();

        return terms.getBuckets().stream()
                .map(b -> {
                    org.opensearch.search.aggregations.metrics.Cardinality card =
                            b.getAggregations().get("unique_users");
                    long uniqueCount = card != null ? card.getValue() : b.getDocCount();
                    return new DashboardMetricsDto.WhaleCountByLevel(
                            b.getKeyAsString(), uniqueCount);
                })
                .collect(Collectors.toList());
    }

    private long countInvestorBehaviorSafe() {
        try {
            SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_INVESTOR_BEHAVIOR);
            request.source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(0));
            SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
            return getTotalHitCount(response);
        } catch (Exception e) {
            log.debug("[METRICS] investor behavior count skipped: {}", e.getMessage());
            return 0L;
        }
    }

    private List<DashboardMetricsDto.WhaleCountByLevel> getInvestorByLevelSafe() {
        try {
            SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_INVESTOR_BEHAVIOR);
            SearchSourceBuilder src = new SearchSourceBuilder().size(0);
            src.aggregation(
                    AggregationBuilders.terms("inv_level").field("investorLevel").size(10)
                            .subAggregation(AggregationBuilders.cardinality("unique_users_inv").field("userId"))
            );
            request.source(src);
            SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
            if (response.getAggregations() == null) {
                return List.of();
            }
            Terms terms = response.getAggregations().get("inv_level");
            if (terms == null) {
                return List.of();
            }
            return terms.getBuckets().stream()
                    .map(b -> {
                        org.opensearch.search.aggregations.metrics.Cardinality card =
                                b.getAggregations().get("unique_users_inv");
                        long uniqueCount = card != null ? card.getValue() : b.getDocCount();
                        return new DashboardMetricsDto.WhaleCountByLevel(b.getKeyAsString(), uniqueCount);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("[METRICS] investor by level skipped: {}", e.getMessage());
            return List.of();
        }
    }

    private double avgInvestorImpactScoreSafe() {
        try {
            SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_INVESTOR_BEHAVIOR);
            SearchSourceBuilder src = new SearchSourceBuilder().size(0);
            src.aggregation(AggregationBuilders.avg("avg_score").field("portfolioImpactScore"));
            request.source(src);
            SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
            if (response.getAggregations() == null) {
                return 0d;
            }
            var avg = response.getAggregations().get("avg_score");
            if (avg instanceof org.opensearch.search.aggregations.metrics.ParsedAvg parsed) {
                return Double.isFinite(parsed.getValue()) ? parsed.getValue() : 0d;
            }
            return 0d;
        } catch (Exception e) {
            log.debug("[METRICS] investor avg impact skipped: {}", e.getMessage());
            return 0d;
        }
    }

    private double maxInvestorImpactScoreSafe() {
        try {
            SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_INVESTOR_BEHAVIOR);
            SearchSourceBuilder src = new SearchSourceBuilder().size(0);
            src.aggregation(AggregationBuilders.max("max_score").field("portfolioImpactScore"));
            request.source(src);
            SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
            if (response.getAggregations() == null) {
                return 0d;
            }
            var max = response.getAggregations().get("max_score");
            if (max instanceof org.opensearch.search.aggregations.metrics.ParsedMax parsed) {
                return Double.isFinite(parsed.getValue()) ? parsed.getValue() : 0d;
            }
            return 0d;
        } catch (Exception e) {
            log.debug("[METRICS] investor max impact skipped: {}", e.getMessage());
            return 0d;
        }
    }

    private double maxInvestorTotalPortfolioValueSafe() {
        try {
            SearchRequest request = new SearchRequest(OpenSearchIndexerService.INDEX_INVESTOR_BEHAVIOR);
            SearchSourceBuilder src = new SearchSourceBuilder().size(0);
            src.aggregation(AggregationBuilders.max("max_tv").field("totalPortfolioValueTry"));
            request.source(src);
            SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
            if (response.getAggregations() == null) {
                return 0d;
            }
            var max = response.getAggregations().get("max_tv");
            if (max instanceof org.opensearch.search.aggregations.metrics.ParsedMax parsed) {
                return Double.isFinite(parsed.getValue()) ? parsed.getValue() : 0d;
            }
            return 0d;
        } catch (Exception e) {
            log.debug("[METRICS] investor max portfolio value skipped: {}", e.getMessage());
            return 0d;
        }
    }
}
