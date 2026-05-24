package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiAnalysisRequest;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiAnalysisResponse;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiEmailDeliveryDto;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiEmailDeliveryUpsertRequest;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiHistoryListResponse;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiUsageResponse;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.portfolio.ai.PortfolioAiAnalysisService;
import com.nurseli.nrsfinanceportal.application.portfolio.ai.PortfolioAiEmailDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Portfolio AI analizi, geçmiş, e-posta raporu ve teslimat tercihi endpoint'lerini sunar.
 */
@RestController
@RequestMapping("/api/portfolio/ai")
@RequiredArgsConstructor
public class PortfolioAiAnalysisController {

    private final PortfolioAiAnalysisService portfolioAiAnalysisService;
    private final PortfolioAiEmailDeliveryService emailDeliveryService;

    /**
     * {@code usage} — Günlük AI kullanım kotası ve tüketim özetini döner.
     */
    @GetMapping("/usage/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiUsageResponse> usage() {
        return ApiResponse.success(portfolioAiAnalysisService.usage());
    }

    /**
     * {@code analyzeCurrent} — Mevcut portfolio için yeni AI analizi üretir ve kaydeder.
     */
    @PostMapping("/analyses/current/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiAnalysisResponse> analyzeCurrent(
            @Valid @RequestBody PortfolioAiAnalysisRequest request
    ) {
        return ApiResponse.success(portfolioAiAnalysisService.analyzeCurrent(request));
    }

    /**
     * {@code latest} — Kullanıcının en son kayıtlı AI analizini döner.
     */
    @GetMapping("/analyses/latest/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiAnalysisResponse> latest() {
        return ApiResponse.success(portfolioAiAnalysisService.latest());
    }

    /**
     * {@code history} — Zaman aralığı ve metin sorgusu ile AI analiz geçmişini listeler.
     */
    @GetMapping("/analyses/history/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiHistoryListResponse> history(
            @RequestParam(value = "range", defaultValue = "30d") String range,
            @RequestParam(value = "q", required = false) String query
    ) {
        return ApiResponse.success(portfolioAiAnalysisService.history(range, query));
    }

    /**
     * {@code getById} — Kayıtlı {@code ai_output_json} okur; harici AI çağrısı yapmaz.
     */
    @GetMapping("/analyses/{id}/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiAnalysisResponse> getById(@PathVariable String id) {
        return ApiResponse.success(portfolioAiAnalysisService.getById(id));
    }

    /**
     * {@code deleteAnalysis} — Belirtilen analiz kaydını kullanıcıya ait olarak siler.
     */
    @DeleteMapping("/analyses/{id}/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> deleteAnalysis(@PathVariable String id) {
        portfolioAiAnalysisService.deleteAnalysis(id);
        return ApiResponse.success(null);
    }

    /**
     * {@code emailAnalysisReport} — Seçili analiz raporunu kullanıcı e-postasına gönderir.
     */
    @PostMapping("/analyses/{id}/email/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> emailAnalysisReport(@PathVariable String id) {
        portfolioAiAnalysisService.sendAnalysisReportEmail(id);
        return ApiResponse.success(null);
    }

    /**
     * {@code emailDelivery} — Otomatik AI e-posta teslimat tercihlerini okur.
     */
    @GetMapping("/email-delivery/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiEmailDeliveryDto> emailDelivery() {
        return ApiResponse.success(emailDeliveryService.getForCurrentUser());
    }

    /**
     * {@code upsertEmailDelivery} — Otomatik AI e-posta teslimat tercihlerini oluşturur veya günceller.
     */
    @PutMapping("/email-delivery/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiEmailDeliveryDto> upsertEmailDelivery(
            @Valid @RequestBody PortfolioAiEmailDeliveryUpsertRequest request
    ) {
        return ApiResponse.success(emailDeliveryService.upsert(request));
    }
}
