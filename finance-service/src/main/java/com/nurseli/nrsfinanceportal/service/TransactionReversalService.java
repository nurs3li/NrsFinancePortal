package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.event.TransactionReversedEvent;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionReversalService {

    private final TransactionRepository transactionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * ⚠️ Balance mutation YOK
     * Balance TradeService tarafından hesaplanmış olmalı
     */
    @Transactional
    public Transaction reverse(
            Long transactionId,
            BigDecimal balanceAfter
    ) {

        // 1️⃣ Orijinal transaction
        Transaction original = transactionRepository
                .findByIdWithAccountAndUser(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (original.getType() == TransactionType.REVERSAL) {
            throw new IllegalStateException("Reversal of reversal is not allowed");
        }

        if (transactionRepository.existsByReversedTransaction(original)) {
            throw new IllegalStateException("Transaction already reversed");
        }

        // 2️⃣ Reversal transaction (SADECE KAYIT)
        Transaction reversal = Transaction.reversal(
                original,
                currentUserResolver.getOrCreateCurrentUser(),
                balanceAfter
        );

        Transaction saved = transactionRepository.save(reversal);

        // 3️⃣ Event publish
        publishTransactionReversedEvent(saved, original);

        return saved;
    }

    /* ================= EVENT PUBLISH ================= */

    private void publishTransactionReversedEvent(
            Transaction reversal,
            Transaction original
    ) {

        eventPublisher.publishEvent(
                new TransactionReversedEvent(
                        reversal.getId(),
                        original.getId(),
                        original.getAccount().getId(),
                        reversal.getUser().getId(),
                        reversal.getAmount(),
                        reversal.getBalanceAfter(),
                        Instant.now()
                )
        );
    }
}
