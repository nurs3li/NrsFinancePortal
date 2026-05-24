package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.fx.FxEffectiveRatesResponseDto;
import com.nurseli.marketdata.application.fx.FxEffectiveRatesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EVDS döviz + efektif gösterge kurları. Mevcut {@code /api/market/doviz} uçlarından ayrıdır;
 * heatmap ve latest price akışlarını değiştirmez.
 */
@RestController
@RequestMapping({"/api/v1/market/fx", "/api/market/fx"})
@RequiredArgsConstructor
@Slf4j
public class FxEffectiveRatesController {

    private final FxEffectiveRatesService fxEffectiveRatesService;

    @GetMapping("/effective-rates")
    public FxEffectiveRatesResponseDto effectiveRates() {
        try {
            return fxEffectiveRatesService.load();
        } catch (Exception ex) {
            log.warn("[FX_EFFECTIVE] aggregate failed: {}", ex.getMessage());
            return FxEffectiveRatesResponseDto.empty();
        }
    }
}
