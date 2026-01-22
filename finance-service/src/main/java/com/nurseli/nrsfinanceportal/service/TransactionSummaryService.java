package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.TransactionSummaryView;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionSummaryService {

    private final TransactionRepository transactionRepository;
    private final CurrentUserResolver currentUserResolver;

    public TransactionSummaryView getMySummary() {

        Long userId = currentUserResolver
                .getOrCreateCurrentUser()
                .getId();

        BigDecimal totalDeposit = transactionRepository
                .sumDepositsByUser(userId);

        BigDecimal totalWithdraw = transactionRepository
                .sumWithdrawsByUser(userId);

        // 🔒 NULL SAFE (ÇOK ÖNEMLİ)
        if (totalDeposit == null) {
            totalDeposit = BigDecimal.ZERO;
        }

        if (totalWithdraw == null) {
            totalWithdraw = BigDecimal.ZERO;
        }

        return TransactionSummaryView.of(
                totalDeposit,
                totalWithdraw
        );
    }
}
