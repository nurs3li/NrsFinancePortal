package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.repository.MarketPriceBucketView;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class MarketPriceQueryService {

    private final MarketPriceHistoryRepository repository;

    // =========================
    // 🔹 LATEST (OPTIONAL)
    // =========================
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

    // =========================
    // 🔹 HISTORY (TIME BUCKET)
    // =========================
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