package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
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
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BalanceRepository balanceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Transaction recordDeposit(Account account, BigDecimal amount) {

        Balance balance = balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        BigDecimal newBalance = balance.increase(amount);

        Transaction tx = Transaction.deposit(
                account,
                currentUserResolver.getOrCreateCurrentUser(),
                amount,
                newBalance
        );

        balanceRepository.save(balance);
        Transaction saved = transactionRepository.save(tx);

        publishTransactionCreatedEvent(saved);

        return saved;
    }

    @Transactional
    public Transaction recordWithdraw(Account account, BigDecimal amount) {

        Balance balance = balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        BigDecimal newBalance = balance.decrease(amount);

        Transaction tx = Transaction.withdraw(
                account,
                currentUserResolver.getOrCreateCurrentUser(),
                amount,
                newBalance
        );

        balanceRepository.save(balance);
        Transaction saved = transactionRepository.save(tx);

        publishTransactionCreatedEvent(saved);

        return saved;
    }

    /* ================= EVENT PUBLISH ================= */

    private void publishTransactionCreatedEvent(Transaction tx) {
        eventPublisher.publishEvent(
                new TransactionCreatedEvent(
                        tx.getId(),
                        tx.getAccount().getId(),
                        tx.getUser().getId(),
                        tx.getType(),
                        tx.getAmount(),
                        tx.getBalanceAfter(),
                        LocalDateTime.now()
                )
        );
    }
}
