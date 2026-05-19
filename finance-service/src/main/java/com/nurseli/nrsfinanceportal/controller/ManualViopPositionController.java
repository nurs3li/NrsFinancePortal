package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.PositionHistoricalPriceResolveDto;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.dto.viop.*;
import com.nurseli.nrsfinanceportal.service.viop.ManualViopPositionService;
import com.nurseli.nrsfinanceportal.service.viopbond.PositionHistoricalPriceResolverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me/viop-positions")
@RequiredArgsConstructor
public class ManualViopPositionController {

    private final ManualViopPositionService service;
    private final PositionHistoricalPriceResolverService historicalPriceResolver;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<List<ManualViopPositionDto>> list() {
        return ApiResponse.success(service.listMine());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ViopPositionSummaryDto> summary() {
        return ApiResponse.success(service.summaryMine());
    }

    @GetMapping("/resolve-price")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<PositionHistoricalPriceResolveDto> resolvePrice(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(historicalPriceResolver.resolveViop(symbol, date));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualViopPositionDto> create(@Valid @RequestBody ManualViopPositionCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualViopPositionDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ManualViopPositionUpdateRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ManualViopPositionDto> close(
            @PathVariable Long id,
            @Valid @RequestBody ManualViopPositionCloseRequest request) {
        return ApiResponse.success(service.close(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
