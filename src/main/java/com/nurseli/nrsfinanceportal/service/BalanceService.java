package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BalanceService {

    private final BalanceRepository balanceRepository;
    private final TransactionService transactionService;

    public BalanceService(BalanceRepository balanceRepository,
                          TransactionService transactionService) {
        this.balanceRepository = balanceRepository;
        this.transactionService = transactionService;
    }

    /**
     * Account oluşturulurken otomatik Balance açılır
     */
    public Balance createForAccount(Account account) {
        Balance balance = Balance.createFor(account);
        return balanceRepository.save(balance);
    }

    /**
     * FINANCE_MANAGER → bakiye artırır
     * Audit (Transaction) otomatik yazılır
     */
    public Balance increase(Account account, BigDecimal amount) {

        Balance balance = balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        balance.increase(amount);

        // 🔥 AUDIT KAYDI
        transactionService.recordDeposit(account, amount);

        return balanceRepository.save(balance);
    }

    /**
     * USER → bakiye görüntüler
     */
    public Balance getOf(Account account) {
        return balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));
    }
}
