package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 🔑 TEK GERÇEK KAYIT NOKTASI
     *
     * ❗ Balance mutation YOK
     * ❗ BalanceAfter DIŞARIDAN gelir (TradeService)
     */
    @Transactional
    public Transaction record(
            Account account,
            BigDecimal amount,
            TransactionType type,
            BigDecimal balanceAfter
    ) {

        validateAmount(amount);

        Transaction transaction = Transaction.record(
                account,
                currentUserResolver.getOrCreateCurrentUser(),
                amount,
                balanceAfter,
                type
        );

        Transaction saved = transactionRepository.save(transaction);

        publishEventAfterCommit(saved);

        return saved;
    }

    /* ================= EVENT PUBLISH ================= */

    private void publishEventAfterCommit(Transaction transaction) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            eventPublisher.publishEvent(
                                    new TransactionCreatedEvent(
                                            transaction.getId(),
                                            transaction.getAccount().getId(),
                                            transaction.getUser().getId(),
                                            transaction.getType().name(),
                                            transaction.getAmount(),
                                            transaction.getBalanceAfter(),
                                            Instant.now()
                                    )
                            );
                        } catch (Exception ex) {
                            log.error(
                                    "Transaction event publish failed. transactionId={}",
                                    transaction.getId(),
                                    ex
                            );
                        }
                    }
                }
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
