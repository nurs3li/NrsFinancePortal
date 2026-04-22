package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.dto.MarketDashboardResponse;
import com.nurseli.nrsfinanceportal.service.MarketDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MarketDashboardController {

    private final MarketDashboardService service;

    @PreAuthorize("hasAnyRole('USER', 'FINANCE_MANAGER', 'ADMIN')")
    @GetMapping("/api/market/dashboard")
    public MarketDashboardResponse dashboard() {
        return service.buildDashboard();
    }
}
