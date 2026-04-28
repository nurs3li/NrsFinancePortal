package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class VakifBankFxProvider implements FxProvider {

    @Override
    public Map<String, MarketPriceLatestResponse> getLatest() {
        log.warn("[FX_PROVIDER] VakifBank provider is not integrated yet, returning empty latest map");
        return Map.of();
    }

    @Override
    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        log.warn("[FX_PROVIDER] VakifBank history unavailable, fallback required. symbol={}, days={}", symbol, days);
        return List.of();
    }

    @Override
    public String providerName() {
        return "VAKIFBANK";
    }

    @Override
    public boolean isCanonical() {
        return false;
    }
}
