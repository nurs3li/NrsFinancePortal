package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.TransactionSummaryView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.TransactionSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions/summary")
@RequiredArgsConstructor
public class TransactionSummaryController {

    private final TransactionSummaryService summaryService;

    /**
     * USER → kendi transaction özeti
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<TransactionSummaryView> mySummary() {
        return ApiResponse.success(
                summaryService.getMySummary()
        );
    }
}
