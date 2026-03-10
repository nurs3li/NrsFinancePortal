package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.AccountDto;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.service.AccountService;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
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

    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ApiResponse<AccountDto> createMyAccount(
            @RequestParam AccountType type
    ) {
        return ApiResponse.success(
                AccountDto.from(
                        accountService.createAccount(
                                currentUserResolver.getOrCreateCurrentUser(),
                                type
                        )
                )
        );
    }

    @PreAuthorize("hasAnyRole('USER', 'FINANCE_MANAGER', 'ADMIN')")
    @GetMapping("/me")
    public ApiResponse<List<AccountDto>> myAccounts() {
        return ApiResponse.success(
                accountService.getAccountsOf(
                        currentUserResolver.getOrCreateCurrentUser()
                ).stream().map(AccountDto::from).toList()
        );
    }

    @PostMapping("/admin/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER')")
    public ApiResponse<AccountDto> createAccountForUser(
            @PathVariable Long userId,
            @RequestParam AccountType type
    ) {
        return ApiResponse.success(
                AccountDto.from(
                        accountService.createAccountForUser(userId, type)
                )
        );
    }
}