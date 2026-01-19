package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * 💰 Deposit audit kaydı
     */
    public void recordDeposit(Account account, BigDecimal amount) {

        System.out.println(">>> [TX] recordDeposit START");
        System.out.println(">>> accountId = " + account.getId());
        System.out.println(">>> amount = " + amount);

        Transaction tx = Transaction.deposit(account, amount);

        System.out.println(">>> transaction entity created");
        System.out.println(">>> type = " + tx.getType());
        System.out.println(">>> createdAt = " + tx.getCreatedAt());

        transactionRepository.save(tx);

        System.out.println(">>> transaction SAVED (DEPOSIT)");
    }

    /**
     * 💸 Withdraw audit kaydı
     */
    public void recordWithdraw(Account account, BigDecimal amount) {

        System.out.println(">>> [TX] recordWithdraw START");
        System.out.println(">>> accountId = " + account.getId());
        System.out.println(">>> amount = " + amount);

        Transaction tx = Transaction.withdraw(account, amount);

        System.out.println(">>> transaction entity created");
        System.out.println(">>> type = " + tx.getType());
        System.out.println(">>> createdAt = " + tx.getCreatedAt());

        transactionRepository.save(tx);

        System.out.println(">>> transaction SAVED (WITHDRAW)");
    }

    /**
     * 📜 USER → kendi transaction geçmişini görür
     */
    public List<Transaction> getTransactionsOfAccounts(List<Account> accounts) {
        System.out.println(">>> [TX] fetching transactions for accounts: " + accounts.size());
        return transactionRepository.findByAccountInOrderByCreatedAtDesc(accounts);
    }
}

