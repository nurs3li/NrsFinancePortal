package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.TransactionView;
import com.nurseli.nrsfinanceportal.service.TransactionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionQueryService transactionQueryService;

    /**
     * USER → kendi transaction geçmişi
     * GET /api/transactions/me?page=0&size=10
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public Page<TransactionView> myTransactions(Pageable pageable) {
        return transactionQueryService.getMyTransactions(pageable);
    }

    /**
     * USER → kendi transaction geçmişi (DATE RANGE)
     * GET /api/transactions/me/range?start=2026-01-01T00:00:00&end=2026-01-31T23:59:59
     */
    @GetMapping("/me/range")
    @PreAuthorize("hasRole('USER')")
    public Page<TransactionView> myTransactionsWithRange(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime start,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime end,

            Pageable pageable
    ) {
        return transactionQueryService.getMyTransactions(start, end, pageable);
    }

    /**
     * ADMIN / FINANCE_MANAGER → account bazlı transaction geçmişi
     */
    @GetMapping("/admin/account/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER')")
    public Page<TransactionView> accountTransactions(
            @PathVariable Long accountId,
            Pageable pageable
    ) {
        return transactionQueryService.getAccountTransactions(accountId, pageable);
    }
}
