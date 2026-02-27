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

            return new DashboardMetricsDto(trades, whales, suspicious, topSymbols, whaleByLevel);
        } catch (IOException e) {
            log.error("[METRICS] dashboard query failed", e);
            return new DashboardMetricsDto(0, 0, 0, List.of(), List.of());
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
        src.aggregation(AggregationBuilders.terms("by_level").field("whaleLevel").size(10));
        request.source(src);

        SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
        if (response.getAggregations() == null) return List.of();
        Terms terms = response.getAggregations().get("by_level");
        if (terms == null) return List.of();

        return terms.getBuckets().stream()
                .map(b -> new DashboardMetricsDto.WhaleCountByLevel(b.getKeyAsString(), b.getDocCount()))
                .collect(Collectors.toList());
    }
}
