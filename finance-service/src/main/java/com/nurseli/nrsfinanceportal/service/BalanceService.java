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
@Transactional
public class BalanceService {

    private final BalanceRepository balanceRepository;

    /**
     * Returns balance if exists, otherwise creates ZERO balance.
     * Safe for legacy accounts & idempotent.
     */
    public Balance getOrCreate(Account account) {
        return balanceRepository.findByAccount(account)
                .orElseGet(() ->
                        balanceRepository.save(new Balance(account))
                );
    }

    /**
     * Read-only access
     */
    public Balance getOf(Account account) {
        return balanceRepository.findByAccount(account)
                .orElseThrow(() ->
                        new IllegalStateException("Balance not found for account " + account.getId())
                );
    }

    /**
     * Increase balance (ADMIN / DEPOSIT)
     */
    public void increase(Account account, BigDecimal amount) {
        Balance balance = getOrCreate(account); // 🔥 kritik nokta
        balance.increase(amount);
        balanceRepository.save(balance);
    }

    /**
     * Decrease balance (WITHDRAW)
     */
    public void decrease(Account account, BigDecimal amount) {
        Balance balance = getOrCreate(account); // 🔥 kritik nokta
        balance.decrease(amount);
        balanceRepository.save(balance);
    }

    /**
     * Explicit initializer (optional usage)
     */
    public void initialize(Account account) {
        if (balanceRepository.existsByAccount(account)) {
            return; // idempotent
        }
        balanceRepository.save(new Balance(account));
    }
}
