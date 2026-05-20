package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioAnalysisResponse;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioInsightsResponse;
import com.nurseli.nrsfinanceportal.common.dto.PortfolioInsightNotificationEvaluateResponse;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioCloseRequest;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioCreateRequest;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.common.dto.ManualPriceResolveDto;
import com.nurseli.nrsfinanceportal.common.dto.UnifiedPortfolioItemView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.service.ManualPortfolioService;
import com.nurseli.nrsfinanceportal.service.UnifiedPortfolioService;
import com.nurseli.nrsfinanceportal.service.portfolio.ManualPortfolioInsightsService;
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
    private final ManualPortfolioInsightsService manualPortfolioInsightsService;

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

    @GetMapping("/manual/timeseries/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMine(from, to));
    }

    @GetMapping("/manual/timeseries/me/segment")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesSegment(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String mode,
            @RequestParam String key
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineSegment(from, to, mode, key));
    }

    @GetMapping("/manual/timeseries/me/sold-hold-hypothetical")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesSoldHoldHypothetical(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineSoldHoldHypothetical(from, to));
    }

    @GetMapping("/manual/timeseries/me/sold-hold-hypothetical/segment")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesSoldHoldHypotheticalSegment(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String mode,
            @RequestParam String key
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineSoldHoldHypotheticalSegment(from, to, mode, key));
    }

    @GetMapping("/manual/timeseries/me/sold-lifecycle-pnl")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesSoldLifecyclePnl(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineSoldLifecyclePnl(from, to));
    }

    @GetMapping("/manual/timeseries/me/sold-lifecycle-pnl/segment")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesSoldLifecyclePnlSegment(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String mode,
            @RequestParam String key
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineSoldLifecyclePnlSegment(from, to, mode, key));
    }

    @GetMapping("/manual/timeseries/me/open-unrealized-pnl")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesOpenUnrealizedPnl(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineOpenUnrealizedPnl(from, to));
    }

    @GetMapping("/manual/timeseries/me/open-unrealized-pnl/segment")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesOpenUnrealizedPnlSegment(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String mode,
            @RequestParam String key
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineOpenUnrealizedPnlSegment(from, to, mode, key));
    }

    @GetMapping("/manual/{id}/analysis")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioAnalysisResponse> manualAnalysis(@PathVariable Long id) {
        return ApiResponse.success(manualPortfolioService.analysis(id));
    }

    @GetMapping("/manual/insights/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioInsightsResponse> manualInsightsMe() {
        return ApiResponse.success(manualPortfolioInsightsService.insightsForCurrentUser());
    }

    @PostMapping("/manual/insights/evaluate-notifications/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioInsightNotificationEvaluateResponse> evaluatePortfolioInsightNotifications() {
        return ApiResponse.success(manualPortfolioInsightsService.evaluateNotificationsForCurrentUser());
    }

    @GetMapping("/me/unified")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<UnifiedPortfolioItemView>> myUnifiedPortfolio() {
        return ApiResponse.success(unifiedPortfolioService.myUnifiedPortfolio());
    }
}
