package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.application.MarketPriceQueryService;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/market/funds")
@RequiredArgsConstructor
public class FundMarketController {

    private final MarketPriceQueryService queryService;

    @GetMapping("/latest")
    public Map<String, MarketPriceLatestResponse> latestFunds() {
        return queryService.getLatestFunds();
    }
}