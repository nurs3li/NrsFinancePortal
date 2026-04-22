package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/equity")
@RequiredArgsConstructor
public class EquityMarketController {

    private final MarketPriceQueryService queryService;

    @GetMapping("/latest")
    public Map<String, MarketPriceLatestResponse> latest() {
        return new LinkedHashMap<>(queryService.getLatestBySource("FINHUB"));
    }

    /**
     * Günlük (dakika kovası) geçmiş — crypto/metals/funds ile aynı sözleşme.
     * Veri {@code market_price_history} tablosunda FINHUB kaynaklı satırlardan okunur.
     */
    @GetMapping("/history")
    public List<MarketPriceHistoryResponse> history(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "14") int days
    ) {
        return queryService.getEquityHistory(symbol, days);
    }
}