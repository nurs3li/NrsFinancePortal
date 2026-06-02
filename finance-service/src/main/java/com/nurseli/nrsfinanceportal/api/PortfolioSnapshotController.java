package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.PortfolioSnapshotPointDto;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import com.nurseli.nrsfinanceportal.application.dashboard.PortfolioSnapshotQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Kullanıcı portfolio değer snapshot geçmişi sorgu endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/portfolio/snapshots", "/api/portfolio/snapshots"})
@RequiredArgsConstructor
public class PortfolioSnapshotController {

    private final PortfolioSnapshotQueryService portfolioSnapshotQueryService;

    /**
     * {@code mySnapshots} — Tarih aralığı ve opsiyonel tetikleyici tipi ile snapshot noktalarını listeler.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<PortfolioSnapshotPointDto>> mySnapshots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) SnapshotTriggerType trigger
    ) {
        return ApiResponse.success(portfolioSnapshotQueryService.mySnapshots(from, to, trigger));
    }
}
