package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface BalanceRepository extends JpaRepository<Balance, Long> {

    /**
     * Normal read (no lock)
     * Used for balance operations
     */
    Optional<Balance> findByAccount(Account account);

    /**
     * Concurrency-safe read for update operations
     * Used in deposit / withdraw / adjust flows
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Balance b where b.account = :account")
    Optional<Balance> findByAccountForUpdate(Account account);

    /**
     * Used during account initialization
     */
    boolean existsByAccount(Account account);
}
