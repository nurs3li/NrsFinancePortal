package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class FundsWithdrawalService {

    private final BalanceRepository balanceRepository;
    private final TransactionService transactionService;

    @Transactional
    public Transaction withdraw(Account account, BigDecimal amount) {

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Withdraw amount must be positive");
        }

        Balance balance = balanceRepository
                .findByAccountForUpdate(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        if (balance.getAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        // domain update
        balance.decrease(amount);

        // audit + kafka
        return transactionService.recordWithdraw(account, amount);
    }
}

