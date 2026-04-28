package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EtfFundProvider implements FundProvider {
    private final MarketPriceQueryService queryService;

    public EtfFundProvider(MarketPriceQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public Map<String, MarketPriceLatestResponse> getLatest() {
        return queryService.getLatestFunds();
    }

    @Override
    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        return queryService.getHistory(symbol, days);
    }

    @Override
    public String providerName() {
        return "ETF";
    }

    @Override
    public boolean isCanonical() {
        return false;
    }
}
