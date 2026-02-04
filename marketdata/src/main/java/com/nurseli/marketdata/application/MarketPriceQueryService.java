package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.repository.MarketPriceBucketView;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketPriceQueryService {

    private final MarketPriceHistoryRepository repository;

    // =====================================================
    // 🔹 LATEST – SINGLE SYMBOL
    // =====================================================
    public MarketPriceLatestResponse getLatestOrThrow(String symbol) {
        return repository
                .findTopBySymbolOrderByTimestampDesc(symbol)
                .map(e -> new MarketPriceLatestResponse(
                        e.getSymbol(),
                        e.getBuyPrice(),
                        e.getSellPrice(),
                        e.getSource(),
                        e.getTimestamp()
                ))
                .orElseThrow(() ->
                        new IllegalStateException("No data found for symbol: " + symbol)
                );
    }

    // =====================================================
    // 🔹 LATEST – BY SOURCE
    // =====================================================
    public Map<String, MarketPriceLatestResponse> getLatestBySource(String source) {

        return repository.findLatestBySource(source)
                .stream()
                .collect(Collectors.toMap(
                        MarketPriceHistory::getSymbol,
                        e -> new MarketPriceLatestResponse(
                                e.getSymbol(),
                                e.getBuyPrice(),
                                e.getSellPrice(),
                                e.getSource(),
                                e.getTimestamp()
                        ),
                        // Aynı symbol gelirse en güncel timestamp kazanır
                        (a, b) -> a.timestamp().isAfter(b.timestamp()) ? a : b,
                        LinkedHashMap::new
                ));
    }

    // =====================================================
    // 🔹 CONVENIENCE METHODS (SENİN SOURCE’LARIN)
    // =====================================================
    public Map<String, MarketPriceLatestResponse> getLatestCrypto() {
        return getLatestBySource("COINGECKO");
    }

    public Map<String, MarketPriceLatestResponse> getLatestFx() {
        return getLatestBySource("TCMB");
    }

    public Map<String, MarketPriceLatestResponse> getLatestMetals() {
        return getLatestBySource("COINGECKO");
    }

    public Map<String, MarketPriceLatestResponse> getLatestFunds() {
        return getLatestBySource("TEFAS");
    }

    // =====================================================
    // 🔹 HISTORY – TIME BUCKET
    // =====================================================
    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        List<MarketPriceBucketView> buckets =
                repository.findBucketedHistory(symbol, start, end);

        return buckets.stream()
                .map(b -> new MarketPriceHistoryResponse(
                        b.getBuyPrice(),
                        b.getSellPrice(),
                        b.getTimestamp()
                ))
                .toList();
    }
}