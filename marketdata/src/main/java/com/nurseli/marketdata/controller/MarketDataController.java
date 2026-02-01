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
@RequestMapping("/api/market/doviz")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketPriceQueryService queryService;

    // =========================
    // 🔹 DASHBOARD – LATEST (FX)
    // =========================
    @GetMapping("/latest")
    public Map<String, MarketPriceLatestResponse> latest() {

        Map<String, MarketPriceLatestResponse> response = new LinkedHashMap<>();

        response.put("USDTRY", queryService.getLatestOrThrow("USDTRY"));
        response.put("EURTRY", queryService.getLatestOrThrow("EURTRY"));
        response.put("GBPTRY", queryService.getLatestOrThrow("GBPTRY"));

        return response;
    }

    // =========================
    // 🔹 HISTORY – TIME BUCKET
    // =========================
    @GetMapping("/history")
    public List<MarketPriceHistoryResponse> history(
            @RequestParam String symbol,
            @RequestParam int days
    ) {
        return queryService.getHistory(symbol, days);
    }
}