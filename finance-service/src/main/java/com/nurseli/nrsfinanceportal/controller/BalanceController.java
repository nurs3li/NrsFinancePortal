package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.AccountBalanceView;
import com.nurseli.nrsfinanceportal.common.dto.BalanceAdjustmentRequest;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.service.BalanceService;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;
    private final CurrentUserResolver currentUserResolver;
    private final AccountRepository accountRepository;

    /* ================= QUERY ================= */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<AccountBalanceView> myBalance() {

        Account cashAccount = accountRepository
                .findByUserAndType(
                        currentUserResolver.getOrCreateCurrentUser(),
                        AccountType.CASH
                )
                .orElseThrow(() -> new IllegalStateException("Cash account not found"));

        return ApiResponse.success(
                new AccountBalanceView(
                        cashAccount.getId(),
                        balanceService.getOf(cashAccount).getAmount()
                )
        );
    }

    /* ================= ADMIN ADJUST ================= */

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER')")
    public ApiResponse<AccountBalanceView> adjust(
            @Valid @RequestBody BalanceAdjustmentRequest request
    ) {

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        balanceService.increase(account, request.getAmount());

        return ApiResponse.success(
                new AccountBalanceView(
                        account.getId(),
                        balanceService.getOf(account).getAmount()
                )
        );
    }
}
