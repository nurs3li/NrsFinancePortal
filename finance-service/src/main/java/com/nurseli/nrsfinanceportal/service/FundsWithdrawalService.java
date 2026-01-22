package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class FundsWithdrawalService {

    private final BalanceRepository balanceRepository;
    private final TransactionService transactionService;

    public FundsWithdrawalService(
            BalanceRepository balanceRepository,
            TransactionService transactionService
    ) {
        this.balanceRepository = balanceRepository;
        this.transactionService = transactionService;
    }

    @Transactional
    public Balance withdraw(Account account, BigDecimal amount) {

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Withdraw amount must be positive");
        }

        Balance balance = balanceRepository
                .findByAccountForUpdate(account) // 🔒 DB LOCK
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        if (balance.getAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        balance.decrease(amount);

        // 🔥 AUDIT
        transactionService.recordWithdraw(account, amount);

        return balance;
        // save() YOK → dirty checking
    }
}
