package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.dto.MarketOverviewResponse;
import com.nurseli.nrsfinanceportal.service.MarketOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
@RequiredArgsConstructor
public class MarketOverviewController {

    private final MarketOverviewService service;

    @PreAuthorize("hasAnyRole('USER', 'FINANCE_MANAGER', 'ADMIN')")
    @GetMapping("/api/market/overview")
    public MarketOverviewResponse overview() {
        return service.getOverview();
    }
}