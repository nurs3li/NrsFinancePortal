package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.BatchHistoryResponse;
import com.nurseli.marketdata.application.query.MarketPriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.nurseli.marketdata.api.dto.MarketIndicatorsResponse;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/market", "/api/market"})
@RequiredArgsConstructor
public class MarketHistoryController {

    private final MarketPriceQueryService queryService;
    @GetMapping("/indicators")
    public MarketIndicatorsResponse indicators(
            @RequestParam String type,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "180") int days,
            @RequestParam(defaultValue = "7,30,90") String ma,
            /** EQUITY/FX/CRYPTO + saatlik mum: {@code hourly} ise göstergeler bu seriden hesaplanır. */
            @RequestParam(defaultValue = "daily") String bucket
    ) {
        return queryService.getIndicators(type, symbol, days, ma, bucket);
    }
    @GetMapping("/history/batch")
    public BatchHistoryResponse batchHistory(
            @RequestParam String type,
            @RequestParam List<String> symbols,
            @RequestParam(defaultValue = "90") int days,
            /** FX/CRYPTO/EQUITY/METALS {@code hourly}: tick → İstanbul saat dilimi + boş saat taşıma; 1G sonunda 24 nokta. */
            @RequestParam(defaultValue = "daily") String bucket
    ) {
        return queryService.getBatchHistory(type, symbols, days, bucket);
    }
}