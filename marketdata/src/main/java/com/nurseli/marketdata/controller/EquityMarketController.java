package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
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
}