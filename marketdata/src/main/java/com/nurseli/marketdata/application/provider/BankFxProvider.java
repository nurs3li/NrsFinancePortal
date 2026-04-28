package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BankFxProvider implements FxProvider {

    private final VakifBankFxProvider vakifBankFxProvider;

    @Override
    public Map<String, MarketPriceLatestResponse> getLatest() {
        return vakifBankFxProvider.getLatest();
    }

    @Override
    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        return vakifBankFxProvider.getHistory(symbol, days);
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
