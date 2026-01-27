package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.event.TransactionReversedEvent;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
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
    private final BalanceRepository balanceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Transaction reverse(Long transactionId) {

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

        // 2️⃣ Balance
        Balance balance = balanceRepository
                .findByAccount(original.getAccount())
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        // 3️⃣ Balance hesapla
        BigDecimal newBalance =
                original.getType() == TransactionType.DEPOSIT
                        ? balance.decrease(original.getAmount())
                        : balance.increase(original.getAmount());

        // 4️⃣ Reversal transaction
        Transaction reversal = Transaction.reversal(
                original,
                currentUserResolver.getOrCreateCurrentUser(),
                newBalance
        );

        balanceRepository.save(balance);
        Transaction saved = transactionRepository.save(reversal);

        // 5️⃣ Event publish
        publishTransactionReversedEvent(saved, original, balance);

        return saved;
    }

    /* ================= EVENT PUBLISH ================= */

    private void publishTransactionReversedEvent(
            Transaction reversal,
            Transaction original,
            Balance balance
    ) {

        eventPublisher.publishEvent(
                new TransactionReversedEvent(
                        reversal.getId(),
                        original.getId(),
                        original.getAccount().getId(),
                        reversal.getUser().getId(),   // admin / operator
                        reversal.getAmount(),
                        balance.getAmount(),
                        Instant.now()                 // ✅ SADECE Instant
                )
        );
    }
}
