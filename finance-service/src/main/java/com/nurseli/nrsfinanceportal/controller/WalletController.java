package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.WalletDepositInstructionsDto;
import com.nurseli.nrsfinanceportal.common.dto.WalletSummaryDto;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.WalletQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletQueryService walletQueryService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<WalletSummaryDto> summary() {
        return ApiResponse.success(walletQueryService.mySummary());
    }

    @GetMapping("/deposit-instructions")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<WalletDepositInstructionsDto> depositInstructions() {
        return ApiResponse.success(walletQueryService.myDepositInstructions());
    }
}
