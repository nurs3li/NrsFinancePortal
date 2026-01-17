package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Bakiye artışı için audit kaydı
     */
    public void recordDeposit(Account account, BigDecimal amount) {
        transactionRepository.save(
                Transaction.deposit(account, amount)
        );
    }

    /**
     * Bakiye düşüşü için audit kaydı
     */
    public void recordWithdraw(Account account, BigDecimal amount) {
        transactionRepository.save(
                Transaction.withdraw(account, amount)
        );
    }
}
