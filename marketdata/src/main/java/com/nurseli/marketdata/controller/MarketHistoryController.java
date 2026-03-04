package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.BatchHistoryResponse;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.nurseli.marketdata.api.dto.MarketIndicatorsResponse;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketHistoryController {

    private final MarketPriceQueryService queryService;
    @GetMapping("/indicators")
    public MarketIndicatorsResponse indicators(
            @RequestParam String type,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "180") int days,
            @RequestParam(defaultValue = "7,30,90") String ma
    ) {
        return queryService.getIndicators(type, symbol, days, ma);
    }
    @GetMapping("/history/batch")
    public BatchHistoryResponse batchHistory(
            @RequestParam String type,
            @RequestParam List<String> symbols,
            @RequestParam(defaultValue = "90") int days
    ) {
        return queryService.getBatchHistory(type, symbols, days);
    }
}