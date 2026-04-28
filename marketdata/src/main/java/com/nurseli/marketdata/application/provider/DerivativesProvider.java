package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;

import java.util.List;
import java.util.Map;

public interface DerivativesProvider {
    Map<String, MarketPriceLatestResponse> getLatest();
    List<MarketPriceHistoryResponse> getHistory(String symbol, int days);
    String providerName();
    boolean isCanonical();
}
