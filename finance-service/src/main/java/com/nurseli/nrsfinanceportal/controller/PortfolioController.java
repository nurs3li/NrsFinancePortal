package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioAnalysisResponse;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioCloseRequest;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioCreateRequest;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.common.dto.ManualPriceResolveDto;
import com.nurseli.nrsfinanceportal.common.dto.UnifiedPortfolioItemView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.service.ManualPortfolioService;
import com.nurseli.nrsfinanceportal.service.UnifiedPortfolioService;
import com.nurseli.nrsfinanceportal.service.portfolio.ManualPortfolioNominalAnalysisCalculator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final ManualPortfolioService manualPortfolioService;
    private final UnifiedPortfolioService unifiedPortfolioService;
    private final ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioView> addManualPosition(
            @Valid @RequestBody ManualPortfolioCreateRequest request
    ) {
        var saved = manualPortfolioService.create(request);
        return ApiResponse.success(ManualPortfolioView.from(saved, nominalAnalysisCalculator.compute(saved)));
    }

    @GetMapping("/manual/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioView>> myManualPositions() {
        return ApiResponse.success(
                manualPortfolioService.listMine()
                        .stream()
                        .map(p -> ManualPortfolioView.from(p, nominalAnalysisCalculator.compute(p)))
                        .toList()
        );
    }

    @PutMapping("/manual/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioView> updateManualPosition(
            @PathVariable Long id,
            @Valid @RequestBody ManualPortfolioCreateRequest request
    ) {
        var saved = manualPortfolioService.update(id, request);
        return ApiResponse.success(ManualPortfolioView.from(saved, nominalAnalysisCalculator.compute(saved)));
    }

    @PostMapping("/manual/{id}/close")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioView> closeManualPosition(
            @PathVariable Long id,
            @Valid @RequestBody ManualPortfolioCloseRequest request
    ) {
        var saved = manualPortfolioService.close(id, request);
        return ApiResponse.success(ManualPortfolioView.from(saved, nominalAnalysisCalculator.compute(saved)));
    }

    @DeleteMapping("/manual/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> deleteManualPosition(@PathVariable Long id) {
        manualPortfolioService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/manual/price-resolve")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPriceResolveDto> resolveManualPrice(
            @RequestParam AssetType type,
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(manualPortfolioService.resolvePrice(type, symbol, date));
    }

    @GetMapping("/manual/summary/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioSummaryView> manualSummary() {
        return ApiResponse.success(manualPortfolioService.summaryMine());
    }

    @GetMapping("/manual/{id}/analysis")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioAnalysisResponse> manualAnalysis(@PathVariable Long id) {
        return ApiResponse.success(manualPortfolioService.analysis(id));
    }

    @GetMapping("/me/unified")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<UnifiedPortfolioItemView>> myUnifiedPortfolio() {
        return ApiResponse.success(unifiedPortfolioService.myUnifiedPortfolio());
    }
}
