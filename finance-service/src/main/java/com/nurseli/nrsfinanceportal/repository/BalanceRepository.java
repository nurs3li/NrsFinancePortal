package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface BalanceRepository extends JpaRepository<Balance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Balance b where b.account = :account")
    Optional<Balance> findByAccountForUpdate(Account account);

    Optional<Balance> findByAccount(Account account);

}
