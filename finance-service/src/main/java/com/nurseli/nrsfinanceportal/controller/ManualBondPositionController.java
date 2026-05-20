package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.PositionHistoricalPriceResolveDto;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.dto.bond.*;
import com.nurseli.nrsfinanceportal.service.bond.ManualBondPositionService;
import com.nurseli.nrsfinanceportal.service.viopbond.PositionHistoricalPriceResolverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me/bond-positions")
@RequiredArgsConstructor
public class ManualBondPositionController {

    private final ManualBondPositionService service;
    private final PositionHistoricalPriceResolverService historicalPriceResolver;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<List<ManualBondPositionDto>> list() {
        return ApiResponse.success(service.listMine());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<BondPositionSummaryDto> summary() {
        return ApiResponse.success(service.summaryMine());
    }

    @GetMapping("/resolve-price")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<PositionHistoricalPriceResolveDto> resolvePrice(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(historicalPriceResolver.resolveBond(symbol, date));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualBondPositionDto> create(@Valid @RequestBody ManualBondPositionCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualBondPositionDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ManualBondPositionUpdateRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @PostMapping("/{id}/sell")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualBondPositionDto> sell(
            @PathVariable Long id,
            @Valid @RequestBody ManualBondPositionSellRequest request) {
        return ApiResponse.success(service.sell(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
