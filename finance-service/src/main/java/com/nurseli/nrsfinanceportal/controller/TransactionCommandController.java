package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.TransactionView;
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
        public ApiResponse<TransactionView> deposit( // <--- DTO tipi olarak güncellendi
                                                     @PathVariable Long accountId,
                                                     @RequestBody @Valid AmountRequest request
        ) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));

            Transaction saved = transactionService.recordDeposit(account, request.amount());

            // Entity'yi DTO'ya çevirip dönüyoruz
            return ApiResponse.success(TransactionView.from(saved));
        }

        @PostMapping("/withdraw/{accountId}")
        @PreAuthorize("hasRole('USER')")
        public ApiResponse<TransactionView> withdraw( // <--- DTO tipi olarak güncellendi
                                                      @PathVariable Long accountId,
                                                      @RequestBody @Valid AmountRequest request
        ) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));

            Transaction saved = transactionService.recordWithdraw(account, request.amount());

            // Entity'yi DTO'ya çevirip dönüyoruz
            return ApiResponse.success(TransactionView.from(saved));
        }
    public record AmountRequest(BigDecimal amount) {}
}
