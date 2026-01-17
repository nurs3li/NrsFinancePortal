package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.TransactionView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.service.AccountService;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.TransactionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CurrentUserResolver currentUserResolver;

    public TransactionController(
            TransactionService transactionService,
            AccountService accountService,
            CurrentUserResolver currentUserResolver
    ) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * USER → kendi transaction geçmişi
     */
    @GetMapping("/me")
    public ApiResponse<List<TransactionView>> myTransactions() {

        List<Account> accounts =
                accountService.getAccountsOf(
                        currentUserResolver.getOrCreateCurrentUser()
                );

        return ApiResponse.success(
                transactionService.getTransactionsOfAccounts(accounts)
                        .stream()
                        .map(TransactionView::from)
                        .toList()
        );
    }
}
