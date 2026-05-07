package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/metals")
@RequiredArgsConstructor
public class MetalMarketController {

    private final MarketPriceQueryService queryService;

    @GetMapping("/latest")
    public Map<String, MarketPriceLatestResponse> latest() {

        Map<String, MarketPriceLatestResponse> response = new LinkedHashMap<>();

        response.put("XAU_TRY", queryService.getLatestOrThrow("XAU_TRY"));


        return response;
    }
    @GetMapping("/history")
    public List<MarketPriceHistoryResponse> history(
            @RequestParam(defaultValue = "XAU_TRY") String symbol,
            @RequestParam(defaultValue = "7") int days
    ) {
        return queryService.getMetalHistory(symbol, days);
    }
}