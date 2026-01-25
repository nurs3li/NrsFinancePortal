package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.service.AccountService;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * USER → kendi adına account oluşturur
     * POST /api/accounts
     */
    @PostMapping
    public ApiResponse<Account> createMyAccount(
            @RequestParam AccountType type
    ) {
        Account account = accountService.createAccount(
                currentUserResolver.getOrCreateCurrentUser(),
                type
        );
        return ApiResponse.success(account);
    }

    /**
     * USER → kendi account’larını listeler
     * GET /api/accounts/me
     */
    @GetMapping("/me")
    public ApiResponse<List<Account>> myAccounts() {
        return ApiResponse.success(
                accountService.getAccountsOf(
                        currentUserResolver.getOrCreateCurrentUser()
                )
        );
    }

    /**
     * ADMIN / FINANCE_MANAGER → başka user’a account açar
     */
    @PostMapping("/admin/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER')")
    public ApiResponse<Account> createAccountForUser(
            @PathVariable Long userId,
            @RequestParam AccountType type
    ) {
        return ApiResponse.success(
                accountService.createAccountForUser(userId, type)
        );
    }
}
