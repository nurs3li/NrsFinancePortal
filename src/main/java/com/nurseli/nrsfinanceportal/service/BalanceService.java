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
public class BalanceService {

    private final BalanceRepository balanceRepository;
    private final TransactionService transactionService;

    /**
     * Account oluşturulurken otomatik Balance açılır
     */
    @Transactional
    public Balance createForAccount(Account account) {

        return balanceRepository.findByAccount(account)
                .orElseGet(() -> balanceRepository.save(Balance.createFor(account)));
    }

    /**
     * FINANCE_MANAGER → bakiye artırır
     * 🔒 PESSIMISTIC WRITE LOCK
     * 🧾 Transaction audit
     */
    @Transactional
    public Balance increase(Account account, BigDecimal amount) {

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Balance balance = balanceRepository
                .findByAccountForUpdate(account)
                .orElseGet(() -> balanceRepository.save(Balance.createFor(account)));

        balance.increase(amount);

        transactionService.recordDeposit(account, amount);

        return balance;
    }

    /**
     * USER → bakiye görüntüler
     */
    @Transactional(readOnly = true)
    public Balance getOf(Account account) {

        return balanceRepository.findByAccount(account)
                .orElseGet(() -> Balance.createFor(account));
    }
}
