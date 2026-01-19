package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.AccountBalanceView;
import com.nurseli.nrsfinanceportal.common.dto.BalanceAdjustmentRequest;
import com.nurseli.nrsfinanceportal.common.dto.FundsWithdrawalRequest;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.Account;

import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.service.BalanceService;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.FundsWithdrawalService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/balance")
public class BalanceController {

    private final BalanceService balanceService;
    private final FundsWithdrawalService fundsWithdrawalService;
    private final CurrentUserResolver currentUserResolver;
    private final AccountRepository accountRepository;

    public BalanceController(
            BalanceService balanceService,
            FundsWithdrawalService fundsWithdrawalService,
            CurrentUserResolver currentUserResolver,
            AccountRepository accountRepository
    ) {
        this.balanceService = balanceService;
        this.fundsWithdrawalService = fundsWithdrawalService;
        this.currentUserResolver = currentUserResolver;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/me")
    public ApiResponse<AccountBalanceView> myBalance() {
        Account account = accountRepository
                .findByUser(currentUserResolver.getOrCreateCurrentUser())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Account not found"));

        return ApiResponse.success(
                AccountBalanceView.from(balanceService.getOf(account))
        );
    }

    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    @PostMapping("/adjust")
    public ApiResponse<AccountBalanceView> adjust(
            @Valid @RequestBody BalanceAdjustmentRequest request
    ) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        return ApiResponse.success(
                AccountBalanceView.from(
                        balanceService.increase(account, request.getAmount())
                )
        );
    }

    @PostMapping("/withdraw")
    public ApiResponse<AccountBalanceView> withdraw(
            @Valid @RequestBody FundsWithdrawalRequest request
    ) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        return ApiResponse.success(
                AccountBalanceView.from(
                        fundsWithdrawalService.withdraw(account, request.getAmount())
                )
        );
    }
}
