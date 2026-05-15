package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.BatchHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.PreciousMetalUsdOverviewRow;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/metals")
@RequiredArgsConstructor
public class MetalMarketController {

    private final MarketPriceQueryService queryService;

    @GetMapping("/latest")
    public Map<String, MarketPriceLatestResponse> latest() {
        return queryService.getLatestMetals();
    }

    @GetMapping("/precious-usd/overview")
    public List<PreciousMetalUsdOverviewRow> preciousUsdOverview() {
        return queryService.getPreciousMetalUsdOverviewPanel();
    }

    @GetMapping({"/precious-usd/batch-history", "/batch-history"})
    public BatchHistoryResponse batchHistory(
            @RequestParam String symbols,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer days
    ) {
        return queryService.getPreciousMetalUsdBatchHistory(symbols, from, to, days);
    }

    @GetMapping("/{symbol}/latest")
    public MarketPriceLatestResponse symbolLatest(@PathVariable String symbol) {
        return queryService.getMetalSingleLatest(symbol);
    }

    @GetMapping("/history")
    public List<MarketPriceHistoryResponse> history(
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "7") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return queryService.getMetalHistory(symbol, days, from, to);
    }
}
