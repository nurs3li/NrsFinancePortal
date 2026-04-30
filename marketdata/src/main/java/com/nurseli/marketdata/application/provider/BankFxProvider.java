package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankFxProvider implements FxProvider {

    private final VakifBankFxProvider vakifBankFxProvider;
    private final EvdsFxProvider evdsFxProvider;
    @Value("${app.providers.fx.bank-proxy-evds-enabled:true}")
    private boolean bankProxyEvdsEnabled;

    @Override
    public Map<String, MarketPriceLatestResponse> getLatest() {
        Map<String, MarketPriceLatestResponse> bankLatest = vakifBankFxProvider.getLatest();
        if (!bankLatest.isEmpty()) {
            return bankLatest;
        }
        if (!bankProxyEvdsEnabled) {
            return Map.of();
        }
        Map<String, MarketPriceLatestResponse> evdsLatest = evdsFxProvider.getLatest();
        if (!evdsLatest.isEmpty()) {
            // BANK kaynağı boşken ücretsiz ve kontrollü degrade: EVDS ile devam et.
            return new LinkedHashMap<>(evdsLatest);
        }
        log.debug("[FX_PROVIDER] BANK latest empty and EVDS proxy fallback empty.");
        return Map.of();
    }

    @Override
    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        List<MarketPriceHistoryResponse> bankHistory = vakifBankFxProvider.getHistory(symbol, days);
        if (!bankHistory.isEmpty()) {
            return bankHistory;
        }
        if (!bankProxyEvdsEnabled) {
            return List.of();
        }
        List<MarketPriceHistoryResponse> evdsHistory = evdsFxProvider.getHistory(symbol, days);
        if (!evdsHistory.isEmpty()) {
            return evdsHistory;
        }
        return List.of();
    }

    @Override
    public String providerName() {
        return "BANK";
    }

    @Override
    public boolean isCanonical() {
        return false;
    }

    @Override
    public boolean isHealthy() {
        return !getLatest().isEmpty();
    }
}
