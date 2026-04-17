package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.PortfolioPerformanceDto;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.PortfolioPerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio/performance")
@RequiredArgsConstructor
public class PortfolioPerformanceController {

    private final PortfolioPerformanceService portfolioPerformanceService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PortfolioPerformanceDto> myPerformance() {
        return ApiResponse.success(portfolioPerformanceService.myPerformance());
    }
}