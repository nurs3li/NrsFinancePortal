package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
