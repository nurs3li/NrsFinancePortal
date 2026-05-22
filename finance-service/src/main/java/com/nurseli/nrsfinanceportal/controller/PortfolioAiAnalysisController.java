package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiAnalysisRequest;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiAnalysisResponse;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiEmailDeliveryDto;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiEmailDeliveryUpsertRequest;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiHistoryListResponse;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiUsageResponse;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.portfolio.ai.PortfolioAiAnalysisService;
import com.nurseli.nrsfinanceportal.service.portfolio.ai.PortfolioAiEmailDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio/ai")
@RequiredArgsConstructor
public class PortfolioAiAnalysisController {

    private final PortfolioAiAnalysisService portfolioAiAnalysisService;
    private final PortfolioAiEmailDeliveryService emailDeliveryService;

    @GetMapping("/usage/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiUsageResponse> usage() {
        return ApiResponse.success(portfolioAiAnalysisService.usage());
    }

    @PostMapping("/analyses/current/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiAnalysisResponse> analyzeCurrent(
            @Valid @RequestBody PortfolioAiAnalysisRequest request
    ) {
        return ApiResponse.success(portfolioAiAnalysisService.analyzeCurrent(request));
    }

    @GetMapping("/analyses/latest/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiAnalysisResponse> latest() {
        return ApiResponse.success(portfolioAiAnalysisService.latest());
    }

    @GetMapping("/analyses/history/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiHistoryListResponse> history(
            @RequestParam(value = "range", defaultValue = "30d") String range,
            @RequestParam(value = "q", required = false) String query
    ) {
        return ApiResponse.success(portfolioAiAnalysisService.history(range, query));
    }

    /** Kayıtlı {@code ai_output_json} okur; OpenAI çağrısı yapmaz. */
    @GetMapping("/analyses/{id}/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiAnalysisResponse> getById(@PathVariable String id) {
        return ApiResponse.success(portfolioAiAnalysisService.getById(id));
    }

    @DeleteMapping("/analyses/{id}/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> deleteAnalysis(@PathVariable String id) {
        portfolioAiAnalysisService.deleteAnalysis(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/analyses/{id}/email/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> emailAnalysisReport(@PathVariable String id) {
        portfolioAiAnalysisService.sendAnalysisReportEmail(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/email-delivery/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiEmailDeliveryDto> emailDelivery() {
        return ApiResponse.success(emailDeliveryService.getForCurrentUser());
    }

    @PutMapping("/email-delivery/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PortfolioAiEmailDeliveryDto> upsertEmailDelivery(
            @Valid @RequestBody PortfolioAiEmailDeliveryUpsertRequest request
    ) {
        return ApiResponse.success(emailDeliveryService.upsert(request));
    }
}
