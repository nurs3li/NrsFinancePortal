package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.dto.MarketOverviewResponse;
import com.nurseli.nrsfinanceportal.service.MarketOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MarketOverviewController {

    private final MarketOverviewService service;

    @GetMapping("/api/market/overview")
    public MarketOverviewResponse overview() {
        return service.getOverview();
    }
}