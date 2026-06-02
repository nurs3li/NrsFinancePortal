package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.SimulationResponseDto;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.asset.SimulationDisplayCurrency;
import com.nurseli.nrsfinanceportal.application.simulation.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Geçmiş tarihli yatırım senaryosu (what-if) simülasyon endpoint'ini sunar.
 */
@RestController
@RequestMapping({"/api/v1/simulation", "/api/simulation"})
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * {@code simulate} — Varlık tipi, sembol, tutar ve tarih ile getiri simülasyonu DTO'su üretir.
     */
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<SimulationResponseDto> simulate(
            @RequestParam AssetType type,
            @RequestParam String symbol,
            @RequestParam BigDecimal amount,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BigDecimal buyPrice,
            @RequestParam(defaultValue = "TRY") String currency
    ) {
        return ApiResponse.success(
                simulationService.simulate(
                        type,
                        symbol,
                        amount,
                        date,
                        buyPrice,
                        SimulationDisplayCurrency.parse(currency)
                )
        );
    }
}