package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FxProvider {
    Map<String, MarketPriceLatestResponse> getLatest();
    List<MarketPriceHistoryResponse> getHistory(String symbol, int days);
    default MarketPriceLatestResponse getLatest(String symbol) {
        return getLatest().get(symbol);
    }
    default boolean isHealthy() {
        return true;
    }
    default String getName() {
        return providerName();
    }
    default List<MarketPriceHistoryResponse> getHistoryByDateRange(String symbol, LocalDate from, LocalDate to) {
        return getHistory(symbol, Math.max(1, (int) (to.toEpochDay() - from.toEpochDay())));
    }
    String providerName();
    boolean isCanonical();
}
