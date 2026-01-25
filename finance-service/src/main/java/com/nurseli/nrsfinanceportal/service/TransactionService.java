package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BalanceRepository balanceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    /* ================= DEPOSIT ================= */

    @Transactional
    public Transaction recordDeposit(Account account, BigDecimal amount) {

        validateAmount(amount);

        Balance balance = balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        BigDecimal newBalance = balance.increase(amount);

        Transaction transaction = Transaction.deposit(
                account,
                currentUserResolver.getOrCreateCurrentUser(),
                amount,
                newBalance
        );

        balanceRepository.save(balance);
        Transaction saved = transactionRepository.save(transaction);

        publishEventAfterCommit(saved);
        return saved;
    }

    /* ================= WITHDRAW ================= */

    @Transactional
    public Transaction recordWithdraw(Account account, BigDecimal amount) {

        validateAmount(amount);

        Balance balance = balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        if (balance.getAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        BigDecimal newBalance = balance.decrease(amount);

        Transaction transaction = Transaction.withdraw(
                account,
                currentUserResolver.getOrCreateCurrentUser(),
                amount,
                newBalance
        );

        balanceRepository.save(balance);
        Transaction saved = transactionRepository.save(transaction);

        publishEventAfterCommit(saved);
        return saved;
    }

    /* ================= EVENT (AFTER COMMIT) ================= */

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
                                            transaction.getType(),
                                            transaction.getAmount(),
                                            transaction.getBalanceAfter(),
                                            LocalDateTime.now()
                                    )
                            );
                        } catch (Exception ex) {
                            // ❗ Event failure = LOG ONLY
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

    /* ================= VALIDATION ================= */

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
