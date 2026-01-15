package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BalanceService {

    private final BalanceRepository balanceRepository;

    public BalanceService(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    public Balance createForAccount(Account account) {
        Balance balance = Balance.createFor(account);
        return balanceRepository.save(balance);
    }

    public Balance increase(Account account, BigDecimal amount) {
        Balance balance = balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        balance.increase(amount);
        return balanceRepository.save(balance);
    }

    public Balance getOf(Account account) {
        return balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));
    }
}
