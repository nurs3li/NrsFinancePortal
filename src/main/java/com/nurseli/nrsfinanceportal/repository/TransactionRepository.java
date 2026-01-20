package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 🔹 USER → kendi transaction
    Page<Transaction> findByUserIdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    // 🔹 USER → date range
    Page<Transaction> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    // 🔹 ACCOUNT → admin / finance
    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(
            Long accountId,
            Pageable pageable
    );

    // 🔹 SUMMARY — DEPOSIT
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = com.nurseli.nrsfinanceportal.domain.transaction.TransactionType.DEPOSIT
    """)
    BigDecimal sumDepositsByUser(@Param("userId") Long userId);

    // 🔹 SUMMARY — WITHDRAW
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = com.nurseli.nrsfinanceportal.domain.transaction.TransactionType.WITHDRAW
    """)
    BigDecimal sumWithdrawsByUser(@Param("userId") Long userId);
}
