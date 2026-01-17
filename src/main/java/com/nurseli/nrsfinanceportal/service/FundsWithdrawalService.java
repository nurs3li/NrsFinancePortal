package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FundsWithdrawalService {

    private final BalanceRepository balanceRepository;
    private final TransactionService transactionService;

    public FundsWithdrawalService(BalanceRepository balanceRepository,
                                  TransactionService transactionService) {
        this.balanceRepository = balanceRepository;
        this.transactionService = transactionService;
    }

    public Balance withdraw(Account account, BigDecimal amount) {

        Balance balance = balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        // 🔐 İŞ KURALI: Negatif bakiye OLAMAZ
        if (balance.getAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        // bakiye düş
        balance.decrease(amount);

        // audit kaydı
        transactionService.recordWithdraw(account, amount);

        return balanceRepository.save(balance);
    }
}
