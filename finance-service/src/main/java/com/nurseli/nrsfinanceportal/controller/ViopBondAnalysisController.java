package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.ViopBondCombinedSummaryDto;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.ViopBondAnalysisSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/viop-bond-analysis")
@RequiredArgsConstructor
public class ViopBondAnalysisController {

    private final ViopBondAnalysisSummaryService summaryService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<ViopBondCombinedSummaryDto> combinedSummary() {
        return ApiResponse.success(summaryService.combinedSummary());
    }
}
