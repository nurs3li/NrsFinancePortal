package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.application.provider.ProviderRegistry;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/market/funds", "/api/market/funds"})
@RequiredArgsConstructor
public class FundMarketController {
    private final ProviderRegistry providerRegistry;

    @GetMapping("/latest")
    public Map<String, MarketPriceLatestResponse> latestFunds() {
        Map<String, MarketPriceLatestResponse> latest = providerRegistry.fundCanonical().getLatest();
        if (!latest.isEmpty()) {
            return latest;
        }
        for (var fallback : providerRegistry.fundFallbackOrder()) {
            Map<String, MarketPriceLatestResponse> fallbackLatest = fallback.getLatest();
            if (!fallbackLatest.isEmpty()) {
                return fallbackLatest;
            }
        }
        return latest;
    }

    @GetMapping("/history")
    public List<MarketPriceHistoryResponse> history(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "7") int days
    ) {
        List<MarketPriceHistoryResponse> history = providerRegistry.fundCanonical().getHistory(symbol, days);
        if (!history.isEmpty()) {
            return history;
        }
        for (var fallback : providerRegistry.fundFallbackOrder()) {
            List<MarketPriceHistoryResponse> fallbackHistory = fallback.getHistory(symbol, days);
            if (!fallbackHistory.isEmpty()) {
                return fallbackHistory;
            }
        }
        return history;
    }
}