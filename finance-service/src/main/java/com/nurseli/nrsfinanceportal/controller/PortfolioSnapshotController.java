package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.PortfolioSnapshotPointDto;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import com.nurseli.nrsfinanceportal.service.PortfolioSnapshotQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/portfolio/snapshots")
@RequiredArgsConstructor
public class PortfolioSnapshotController {

    private final PortfolioSnapshotQueryService portfolioSnapshotQueryService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER','FINANCE_MANAGER','ADMIN')")
    public ApiResponse<List<PortfolioSnapshotPointDto>> mySnapshots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) SnapshotTriggerType trigger
    ) {
        return ApiResponse.success(portfolioSnapshotQueryService.mySnapshots(from, to, trigger));
    }
}
