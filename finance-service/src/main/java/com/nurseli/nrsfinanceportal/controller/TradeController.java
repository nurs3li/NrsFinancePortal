package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.trade.TradeRequest;
import com.nurseli.nrsfinanceportal.service.trade.TradeResponse;
import com.nurseli.nrsfinanceportal.service.trade.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    /**
     * USER → DEMO account üzerinden al / sat
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<TradeResponse> trade(
            @Valid @RequestBody TradeRequest request
    ) {
        TradeResponse response = tradeService.execute(request);
        return ApiResponse.success(response);
    }
}
