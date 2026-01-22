package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {



    boolean existsByReversedTransaction(Transaction original);

    @Query("""
        select t
        from Transaction t
        join fetch t.account
        join fetch t.user
        where t.id = :id
    """)
    Optional<Transaction> findByIdWithAccountAndUser(@Param("id") Long id);

    Optional<Transaction> findById(Long id);

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
