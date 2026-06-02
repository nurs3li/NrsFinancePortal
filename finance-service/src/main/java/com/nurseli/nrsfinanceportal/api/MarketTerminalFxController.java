package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.UsdTryRateResponse;
import com.nurseli.nrsfinanceportal.application.market.MarketTerminalFxService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Terminal FX göstergeleri için USD/TRY kur endpoint'ini sunar.
 */
@RestController
@RequiredArgsConstructor
public class MarketTerminalFxController {

    private final MarketTerminalFxService marketTerminalFxService;

    /**
     * {@code usdTryRate} — Güncel USD/TRY kuru response DTO'sunu döner.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping({"/api/v1/market/terminal/usd-try-rate", "/api/market/terminal/usd-try-rate"})
    public UsdTryRateResponse usdTryRate() {
        return marketTerminalFxService.usdTryRate();
    }
}
