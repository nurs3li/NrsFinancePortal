package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioAnalysisResponse;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioInsightsResponse;
import com.nurseli.nrsfinanceportal.api.dto.PortfolioInsightNotificationEvaluateResponse;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioCloseRequest;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioCreateRequest;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPriceResolveDto;
import com.nurseli.nrsfinanceportal.api.dto.UnifiedPortfolioItemView;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.application.ManualPortfolioService;
import com.nurseli.nrsfinanceportal.application.UnifiedPortfolioService;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioPageResponse;
import com.nurseli.nrsfinanceportal.application.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioInsightsService;
import com.nurseli.nrsfinanceportal.application.portfolio.ManualPortfolioViewAssembler;
import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioReadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Manuel portfolio pozisyonları, zaman serileri, içgörüler ve birleşik portfolio görünümü endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/portfolio", "/api/portfolio"})
@RequiredArgsConstructor
public class PortfolioController {

    private final ManualPortfolioService manualPortfolioService;
    private final UnifiedPortfolioService unifiedPortfolioService;
    private final ManualPortfolioViewAssembler manualPortfolioViewAssembler;
    private final ManualPortfolioInsightsService manualPortfolioInsightsService;
    private final ManualPortfolioReadService manualPortfolioReadService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * {@code manualPortfolioPage} — Spot portföy sayfası için positions + summary + insights + 6M grafik tek yanıtta.
     */
    @GetMapping("/manual/page/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioPageResponse> manualPortfolioPage() {
        Long userId = currentUserResolver.getCurrentUserId();
        return ApiResponse.success(manualPortfolioReadService.getPageBundle(userId));
    }

    /**
     * {@code addManualPosition} — Yeni manuel portfolio pozisyonu oluşturur.
     */
    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioView> addManualPosition(
            @Valid @RequestBody ManualPortfolioCreateRequest request
    ) {
        var saved = manualPortfolioService.create(request);
        return ApiResponse.success(manualPortfolioViewAssembler.toView(saved));
    }

    /**
     * {@code myManualPositions} — Kullanıcının tüm manuel pozisyonlarını listeler.
     */
    @GetMapping("/manual/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioView>> myManualPositions() {
        Long userId = currentUserResolver.getCurrentUserId();
        return ApiResponse.success(manualPortfolioReadService.getViews(userId));
    }

    /**
     * {@code updateManualPosition} — Mevcut manuel pozisyonu günceller.
     */
    @PutMapping("/manual/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioView> updateManualPosition(
            @PathVariable Long id,
            @Valid @RequestBody ManualPortfolioCreateRequest request
    ) {
        var saved = manualPortfolioService.update(id, request);
        return ApiResponse.success(manualPortfolioViewAssembler.toView(saved));
    }

    /**
     * {@code closeManualPosition} — Manuel pozisyonu satış/kapanış ile sonlandırır.
     */
    @PostMapping("/manual/{id}/close")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioView> closeManualPosition(
            @PathVariable Long id,
            @Valid @RequestBody ManualPortfolioCloseRequest request
    ) {
        var saved = manualPortfolioService.close(id, request);
        return ApiResponse.success(manualPortfolioViewAssembler.toView(saved));
    }

    /**
     * {@code deleteManualPosition} — Manuel pozisyonu kalıcı olarak siler.
     */
    @DeleteMapping("/manual/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> deleteManualPosition(@PathVariable Long id) {
        manualPortfolioService.delete(id);
        return ApiResponse.success(null);
    }

    /**
     * {@code resolveManualPrice} — Varlık tipi, sembol ve tarih için geçmiş fiyat çözümler.
     */
    @GetMapping("/manual/price-resolve")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPriceResolveDto> resolveManualPrice(
            @RequestParam AssetType type,
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(manualPortfolioService.resolvePrice(type, symbol, date));
    }

    /**
     * {@code manualSummary} — Manuel portfolio özet görünümünü döner.
     */
    @GetMapping("/manual/summary/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioSummaryView> manualSummary() {
        Long userId = currentUserResolver.getCurrentUserId();
        return ApiResponse.success(manualPortfolioReadService.getSummary(userId));
    }

    /**
     * {@code manualTimeseries} — Tarih aralığında portfolio değer zaman serisini döner.
     */
    @GetMapping("/manual/timeseries/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long userId = currentUserResolver.getCurrentUserId();
        return ApiResponse.success(manualPortfolioReadService.getTimeseries(userId, from, to));
    }

    /**
     * {@code manualTimeseriesSegment} — Mod ve anahtar ile segmentlenmiş zaman serisini döner.
     */
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

    /**
     * {@code manualTimeseriesSoldHoldHypothetical} — Satılmış pozisyonların tutulduğu varsayımıyla hipotetik zaman serisini döner.
     */
    @GetMapping("/manual/timeseries/me/sold-hold-hypothetical")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesSoldHoldHypothetical(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineSoldHoldHypothetical(from, to));
    }

    /**
     * {@code manualTimeseriesSoldHoldHypotheticalSegment} — Hipotetik tutma senaryosunun segmentlenmiş zaman serisini döner.
     */
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

    /**
     * {@code manualTimeseriesSoldLifecyclePnl} — Satılmış pozisyonların yaşam döngüsü PnL zaman serisini döner.
     */
    @GetMapping("/manual/timeseries/me/sold-lifecycle-pnl")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesSoldLifecyclePnl(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineSoldLifecyclePnl(from, to));
    }

    /**
     * {@code manualTimeseriesSoldLifecyclePnlSegment} — Satılmış pozisyon PnL zaman serisinin segmentlenmiş halini döner.
     */
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

    /**
     * {@code manualTimeseriesRealReturnPnl} — Enflasyon düzeltmeli reel getiri PnL zaman serisini döner.
     */
    @GetMapping("/manual/timeseries/me/real-return-pnl")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesRealReturnPnl(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineRealReturn(from, to));
    }

    /**
     * {@code manualTimeseriesRealReturnPnlSegment} — Reel getiri PnL zaman serisinin segmentlenmiş halini döner.
     */
    @GetMapping("/manual/timeseries/me/real-return-pnl/segment")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesRealReturnPnlSegment(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String mode,
            @RequestParam String key
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineRealReturnSegment(from, to, mode, key));
    }

    /**
     * {@code manualTimeseriesOpenUnrealizedPnl} — Açık pozisyonların gerçekleşmemiş PnL zaman serisini döner.
     */
    @GetMapping("/manual/timeseries/me/open-unrealized-pnl")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioTimeseriesPointDto>> manualTimeseriesOpenUnrealizedPnl(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(manualPortfolioService.timeseriesMineOpenUnrealizedPnl(from, to));
    }

    /**
     * {@code manualTimeseriesOpenUnrealizedPnlSegment} — Gerçekleşmemiş PnL zaman serisinin segmentlenmiş halini döner.
     */
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

    /**
     * {@code manualAnalysis} — Tek manuel pozisyon için detaylı analiz DTO'sunu döner.
     */
    @GetMapping("/manual/{id}/analysis")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioAnalysisResponse> manualAnalysis(@PathVariable Long id) {
        return ApiResponse.success(manualPortfolioService.analysis(id));
    }

    /**
     * {@code manualInsightsMe} — Portfolio içgörü ve risk özetlerini döner.
     */
    @GetMapping("/manual/insights/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioInsightsResponse> manualInsightsMe() {
        Long userId = currentUserResolver.getCurrentUserId();
        return ApiResponse.success(manualPortfolioReadService.getInsights(userId));
    }

    /**
     * {@code evaluatePortfolioInsightNotifications} — İçgörü kurallarına göre bildirim değerlendirmesi yapar.
     */
    @PostMapping("/manual/insights/evaluate-notifications/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioInsightNotificationEvaluateResponse> evaluatePortfolioInsightNotifications() {
        return ApiResponse.success(manualPortfolioInsightsService.evaluateNotificationsForCurrentUser());
    }

    /**
     * {@code myUnifiedPortfolio} — Manuel, VİOP ve tahvil pozisyonlarını birleşik liste görünümünde döner.
     */
    @GetMapping("/me/unified")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<UnifiedPortfolioItemView>> myUnifiedPortfolio() {
        return ApiResponse.success(unifiedPortfolioService.myUnifiedPortfolio());
    }
}
