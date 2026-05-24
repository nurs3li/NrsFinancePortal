package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.ViopBondCombinedSummaryDto;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.ViopBondAnalysisSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * VİOP ve tahvil pozisyonlarının birleşik analiz özeti endpoint'ini sunar.
 */
@RestController
@RequestMapping({"/api/v1/me/viop-bond-analysis", "/api/me/viop-bond-analysis"})
@RequiredArgsConstructor
public class ViopBondAnalysisController {

    private final ViopBondAnalysisSummaryService summaryService;

    /**
     * {@code combinedSummary} — VİOP ve tahvil portföylerinin birleşik özet DTO'sunu döner.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ViopBondCombinedSummaryDto> combinedSummary() {
        return ApiResponse.success(summaryService.combinedSummary());
    }
}
