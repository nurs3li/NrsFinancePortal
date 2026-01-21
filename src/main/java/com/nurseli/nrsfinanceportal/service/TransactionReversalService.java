package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;

import com.nurseli.nrsfinanceportal.repository.BalanceRepository;

@Service
@RequiredArgsConstructor
public class TransactionReversalService {

    private final TransactionRepository transactionRepository;
    private final BalanceRepository balanceRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public Transaction reverse(Long transactionId) {

        Transaction original = transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (original.getType() == TransactionType.REVERSAL) {
            throw new IllegalStateException("Reversal of reversal is not allowed");
        }

        if (transactionRepository.existsByReversedTransaction(original)) {
            throw new IllegalStateException("Transaction already reversed");
        }

        Balance balance = balanceRepository
                .findByAccount(original.getAccount())
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        BigDecimal newBalance;

        if (original.getType() == TransactionType.DEPOSIT) {
            newBalance = balance.decrease(original.getAmount());
        } else {
            newBalance = balance.increase(original.getAmount());
        }

        Transaction reversal = Transaction.reversal(
                original,
                currentUserResolver.getOrCreateCurrentUser(),
                newBalance
        );

        balanceRepository.save(balance);
        return transactionRepository.save(reversal);
    }
}
