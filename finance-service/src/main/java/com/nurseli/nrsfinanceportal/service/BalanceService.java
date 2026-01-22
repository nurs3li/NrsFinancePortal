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

    public Balance getOf(Account account) {
        return balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found for account"));
    }

    public void increase(Account account, BigDecimal amount) {
        Balance balance = getOf(account);
        balance.increase(amount);
        balanceRepository.save(balance);
    }

    public void decrease(Account account, BigDecimal amount) {
        Balance balance = getOf(account);
        balance.decrease(amount);
        balanceRepository.save(balance);
    }
}
