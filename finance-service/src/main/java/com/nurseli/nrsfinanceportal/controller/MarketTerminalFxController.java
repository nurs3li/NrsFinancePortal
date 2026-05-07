package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.dto.UsdTryRateResponse;
import com.nurseli.nrsfinanceportal.service.MarketTerminalFxService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MarketTerminalFxController {

    private final MarketTerminalFxService marketTerminalFxService;

    @PreAuthorize("hasAnyRole('USER', 'FINANCE_MANAGER', 'ADMIN')")
    @GetMapping("/api/market/terminal/usd-try-rate")
    public UsdTryRateResponse usdTryRate() {
        return marketTerminalFxService.usdTryRate();
    }
}
