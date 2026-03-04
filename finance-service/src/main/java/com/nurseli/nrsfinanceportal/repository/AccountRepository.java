package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountStatus;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Page<Account> findAll(Pageable pageable);
    Page<Account> findByStatus(AccountStatus status, Pageable pageable);

    boolean existsByUser_IdAndStatus(Long userId, AccountStatus status);

    List<Account> findByUser(User user);
    List<Account> findByUser_Id(Long userId);

    Optional<Account> findByUserAndType(User user, AccountType type);

    // ❌ Optional yerine List → birden fazla hesap varsa hata atmasın
    List<Account> findByUserIdAndType(Long userId, AccountType type);
}