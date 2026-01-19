package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        System.out.println(">>> [BALANCE] createForAccount");
        System.out.println(">>> accountId = " + account.getId());

        Balance balance = Balance.createFor(account);
        Balance saved = balanceRepository.save(balance);

        System.out.println(">>> balance CREATED with amount = " + saved.getAmount());

        return saved;
    }

    /**
     * FINANCE_MANAGER → bakiye artırır
     * 🔒 PESSIMISTIC LOCK + 🧾 AUDIT
     */
    @Transactional
    public Balance increase(Account account, BigDecimal amount) {

        Balance balance = balanceRepository
                .findByAccountForUpdate(account)
                .orElseGet(() -> {
                    Balance created = Balance.createFor(account);
                    return balanceRepository.save(created);
                });

        balance.increase(amount);

        transactionService.recordDeposit(account, amount);

        return balance;
    }

    /**
     * USER → bakiye görüntüler
     */
    @Transactional(readOnly = true)
    public Balance getOf(Account account) {

        System.out.println(">>> [BALANCE] getOf");
        System.out.println(">>> accountId = " + account.getId());

        return balanceRepository.findByAccount(account)
                .orElseGet(() -> {
                    System.out.println(">>> balance NOT FOUND → creating");
                    return createForAccount(account);
                });
    }
}
