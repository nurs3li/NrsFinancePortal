package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.user.User;
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
        return transactionRepository.save(tx);
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
        return transactionRepository.save(tx);
    }
}
