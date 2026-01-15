package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.AccountResponse;
import com.nurseli.nrsfinanceportal.common.dto.CreateAccountRequest;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.service.AccountService;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserResolver currentUserResolver;

    public AccountController(AccountService accountService,
                             CurrentUserResolver currentUserResolver) {
        this.accountService = accountService;
        this.currentUserResolver = currentUserResolver;
    }

    // 🔹 USER → kendi account’larını görür
    @GetMapping("/me")
    public ApiResponse<List<AccountResponse>> myAccounts() {
        return ApiResponse.success(
                accountService
                        .getAccountsOf(currentUserResolver.getOrCreateCurrentUser())
                        .stream()
                        .map(AccountResponse::from)
                        .toList()
        );
    }

    // 🔹 FINANCE_MANAGER → başka user’a account açar
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    @PostMapping
    public ApiResponse<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request
    ) {
        AccountType type = AccountType.valueOf(request.getAccountType());

        return ApiResponse.success(
                AccountResponse.from(
                        accountService.createAccountForUser(
                                request.getUserId(),
                                type
                        )
                )
        );
    }
}
