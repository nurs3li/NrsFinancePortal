package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Birden fazla account'a ait transaction'ları
     * en yeni işlem üstte olacak şekilde getirir
     */
    List<Transaction> findByAccountInOrderByCreatedAtDesc(List<Account> accounts);
}
