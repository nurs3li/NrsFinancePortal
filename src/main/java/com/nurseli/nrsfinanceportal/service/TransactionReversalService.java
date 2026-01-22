
package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.event.TransactionReversedEvent;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionReversalService {

    private final TransactionRepository transactionRepository;
    private final BalanceRepository balanceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Transaction reverse(Long transactionId) {

        Transaction original = transactionRepository
                .findByIdWithAccountAndUser(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (original.getType() == TransactionType.REVERSAL) {
            throw new IllegalStateException("Reversal of reversal is not allowed");
        }

        if (transactionRepository.existsByReversedTransaction(original)) {
            throw new IllegalStateException("Transaction already reversed");
        }

        Balance balance = balanceRepository
                .findByAccount(original.getAccount())
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        BigDecimal newBalance =
                original.getType() == TransactionType.DEPOSIT
                        ? balance.decrease(original.getAmount())
                        : balance.increase(original.getAmount());

        Transaction reversal = Transaction.reversal(
                original,
                currentUserResolver.getOrCreateCurrentUser(),
                newBalance
        );

        balanceRepository.save(balance);
        Transaction saved = transactionRepository.save(reversal);

        publishTransactionReversedEvent(saved, original);

        return saved;
    }

    /* ================= EVENT PUBLISH ================= */

    private void publishTransactionReversedEvent(Transaction reversal, Transaction original) {
        eventPublisher.publishEvent(
                new TransactionReversedEvent(
                        reversal.getId(),
                        original.getId(),
                        reversal.getAccount().getId(),
                        reversal.getUser().getId(),
                        reversal.getAmount(),
                        reversal.getBalanceAfter(),
                        LocalDateTime.now()
                )
        );
    }
}
