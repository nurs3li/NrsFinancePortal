package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.AccountBalanceView;
import com.nurseli.nrsfinanceportal.common.dto.BalanceAdjustmentRequest;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.service.BalanceService;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/balance")
public class BalanceController {

    private final BalanceService balanceService;
    private final CurrentUserResolver currentUserResolver;
    private final AccountRepository accountRepository;

    public BalanceController(BalanceService balanceService,
                             CurrentUserResolver currentUserResolver,
                             AccountRepository accountRepository) {
        this.balanceService = balanceService;
        this.currentUserResolver = currentUserResolver;
        this.accountRepository = accountRepository;
    }

    /**
     * USER → kendi bakiyesini görür
     * GET /api/balance/me
     */
    @GetMapping("/me")
    public ApiResponse<AccountBalanceView> myBalance() {

        Account account = accountRepository
                .findByUser(currentUserResolver.getOrCreateCurrentUser())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Account not found"));

        Balance balance = balanceService.getOf(account);

        return ApiResponse.success(
                AccountBalanceView.from(balance)
        );
    }

    /**
     * FINANCE_MANAGER → bakiye ayarlaması yapar
     * POST /api/balance/adjust
     */
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    @PostMapping("/adjust")
    public ApiResponse<AccountBalanceView> adjustBalance(
            @Valid @RequestBody BalanceAdjustmentRequest request
    ) {

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        Balance balance = balanceService.increase(
                account,
                request.getAmount()
        );

        return ApiResponse.success(
                AccountBalanceView.from(balance)
        );
    }
}
