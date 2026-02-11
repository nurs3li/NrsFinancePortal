package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
public interface AccountRepository extends JpaRepository<Account, Long> {

    // MEVCUT
    List<Account> findByUser(User user);

    Optional<Account> findByUserAndType(User user, AccountType type);

    // ✅ YENİ – Dashboard / Read model için
    Optional<Account> findByUserIdAndType(Long userId, AccountType type);
}
