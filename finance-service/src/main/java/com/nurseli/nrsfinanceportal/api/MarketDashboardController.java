package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.MarketDashboardResponse;
import com.nurseli.nrsfinanceportal.application.MarketDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Piyasa terminali dashboard bileşenleri için özet endpoint'ini sunar.
 */
@RestController
@RequiredArgsConstructor
public class MarketDashboardController {

    private final MarketDashboardService service;

    /**
     * {@code dashboard} — Hisse, fon, FX ve kripto özetlerini içeren dashboard DTO'sunu döner.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/api/market/dashboard")
    public MarketDashboardResponse dashboard() {
        return service.buildDashboard();
    }
}
