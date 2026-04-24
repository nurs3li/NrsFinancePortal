package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.TransactionView;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final CurrentUserResolver currentUserResolver;

    /**
     * USER → kendi transaction geçmişi
     */
    public Page<TransactionView> getMyTransactions(Pageable pageable) {
        Long userId = currentUserResolver
                .getOrCreateCurrentUser()
                .getId();

        return transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(TransactionView::from);
    }

    /**
     * USER → kendi transaction geçmişi (DATE RANGE)
     */
    public Page<TransactionView> getMyTransactions(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    ) {
        Long userId = currentUserResolver
                .getOrCreateCurrentUser()
                .getId();

        return transactionRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        userId,
                        start,
                        end,
                        pageable
                )
                .map(TransactionView::from);
    }

    /**
     * ADMIN / FINANCE_MANAGER → account bazlı transaction geçmişi
     */
    public Page<TransactionView> getAccountTransactions(
            Long accountId,
            Pageable pageable
    ) {
        return transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(TransactionView::from);
    }
}
