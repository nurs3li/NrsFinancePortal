package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class FundsWithdrawalService {

    private final BalanceRepository balanceRepository;

    @Transactional
    public void withdraw(Account account, BigDecimal amount) {
        if (account == null) {
            throw new IllegalArgumentException("Account is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        Balance balance = balanceRepository.findByAccountForUpdate(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found for account " + account.getId()));
        balance.decrease(amount);
        balanceRepository.save(balance);
    }
}
