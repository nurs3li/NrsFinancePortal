package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioCreateRequest;
import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.common.dto.UnifiedPortfolioItemView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.ManualPortfolioService;
import com.nurseli.nrsfinanceportal.service.UnifiedPortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final ManualPortfolioService manualPortfolioService;
    private final UnifiedPortfolioService unifiedPortfolioService;

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioView> addManualPosition(
            @Valid @RequestBody ManualPortfolioCreateRequest request
    ) {
        return ApiResponse.success(
                ManualPortfolioView.from(manualPortfolioService.create(request))
        );
    }

    @GetMapping("/manual/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<ManualPortfolioView>> myManualPositions() {
        return ApiResponse.success(
                manualPortfolioService.listMine()
                        .stream()
                        .map(ManualPortfolioView::from)
                        .toList()
        );
    }

    @PutMapping("/manual/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<ManualPortfolioView> updateManualPosition(
            @PathVariable Long id,
            @Valid @RequestBody ManualPortfolioCreateRequest request
    ) {
        return ApiResponse.success(
                ManualPortfolioView.from(manualPortfolioService.update(id, request))
        );
    }

    @DeleteMapping("/manual/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> deleteManualPosition(@PathVariable Long id) {
        manualPortfolioService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/me/unified")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<UnifiedPortfolioItemView>> myUnifiedPortfolio() {
        return ApiResponse.success(unifiedPortfolioService.myUnifiedPortfolio());
    }
}