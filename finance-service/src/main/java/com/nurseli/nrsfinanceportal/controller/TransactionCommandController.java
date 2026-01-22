package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionCommandController {

    private final TransactionService transactionService;
    private final AccountRepository accountRepository;

    @PostMapping("/deposit/{accountId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Transaction> deposit(
            @PathVariable Long accountId,
            @RequestBody @Valid AmountRequest request
    ) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        return ApiResponse.success(
                transactionService.recordDeposit(account, request.amount())
        );
    }

    @PostMapping("/withdraw/{accountId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Transaction> withdraw(
            @PathVariable Long accountId,
            @RequestBody @Valid AmountRequest request
    ) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        return ApiResponse.success(
                transactionService.recordWithdraw(account, request.amount())
        );
    }

    public record AmountRequest(BigDecimal amount) {}
}
