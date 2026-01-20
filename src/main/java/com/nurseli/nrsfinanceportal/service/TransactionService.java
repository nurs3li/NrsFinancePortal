package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BalanceRepository balanceRepository;
    private final CurrentUserResolver currentUserResolver;

    /**
     * 💰 Deposit audit kaydı
     */
    @Transactional
    public void recordDeposit(Account account, BigDecimal amount) {

        Balance balance = balanceRepository
                .findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        Transaction tx = Transaction.deposit(
                account,
                currentUserResolver.getOrCreateCurrentUser(),
                amount,
                balance.getAmount()
        );

        transactionRepository.save(tx);
    }

    /**
     * 💸 Withdraw audit kaydı
     */
    @Transactional
    public void recordWithdraw(Account account, BigDecimal amount) {

        Balance balance = balanceRepository
                .findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        Transaction tx = Transaction.withdraw(
                account,
                currentUserResolver.getOrCreateCurrentUser(),
                amount,
                balance.getAmount()
        );

        transactionRepository.save(tx);
    }
}
