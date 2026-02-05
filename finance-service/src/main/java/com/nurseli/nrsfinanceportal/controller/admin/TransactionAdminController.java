package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.TransactionReversalRequest;
import com.nurseli.nrsfinanceportal.common.dto.TransactionView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import com.nurseli.nrsfinanceportal.service.TransactionReversalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/admin/transactions")
@RequiredArgsConstructor
public class TransactionAdminController {

    private final TransactionRepository transactionRepository;
    private final BalanceRepository balanceRepository;
    private final TransactionReversalService reversalService;

    @PostMapping("/reverse")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public ApiResponse<TransactionView> reverse(
            @RequestBody TransactionReversalRequest request
    ) {

        // 1️⃣ Orijinal transaction
        Transaction original = transactionRepository
                .findByIdWithAccountAndUser(request.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        // 2️⃣ Balance (OKUMA)
        Balance balance = balanceRepository
                .findByAccount(original.getAccount())
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        // 3️⃣ Reversal sonrası balance hesapla
        BigDecimal balanceAfter =
                original.getType() == TransactionType.DEPOSIT
                        ? balance.getAmount().subtract(original.getAmount())
                        : balance.getAmount().add(original.getAmount());

        // 4️⃣ Reversal kaydı (mutation YOK)
        Transaction reversal = reversalService.reverse(
                original.getId(),
                balanceAfter
        );

        return ApiResponse.success(TransactionView.from(reversal));
    }
}
