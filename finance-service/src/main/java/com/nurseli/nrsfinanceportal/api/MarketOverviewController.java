package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.MarketOverviewResponse;
import com.nurseli.nrsfinanceportal.application.market.MarketOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Piyasa genel bakış (overview) endpoint'ini sunar.
 */
@RestController
@RequiredArgsConstructor
public class MarketOverviewController {

    private final MarketOverviewService service;

    /**
     * {@code overview} — Piyasa özet response DTO'sunu döner.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping({"/api/v1/market/overview", "/api/market/overview"})
    public MarketOverviewResponse overview() {
        return service.getOverview();
    }
}