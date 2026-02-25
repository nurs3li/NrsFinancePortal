package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.TradeHistoryDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.TradeHistoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeHistoryController {

    private final TradeHistoryQueryService service;
    private final CurrentUserResolver currentUserResolver;

    /**
     * GET /api/trades/history
     *
     * ?page=0&size=20
     * ?assetType=CRYPTO
     * ?sort=tradedAt,desc
     */
    @PreAuthorize("hasAnyRole('USER', 'FINANCE_MANAGER', 'ADMIN')")
    @GetMapping("history")
    public Page<TradeHistoryDto> history(
            @RequestParam(required = false) AssetType assetType,
            Pageable pageable
    ) {

        Long userId = currentUserResolver
                .getOrCreateCurrentUser()
                .getId();

        return service.getTradeHistory(userId, assetType, pageable);
    }
}
