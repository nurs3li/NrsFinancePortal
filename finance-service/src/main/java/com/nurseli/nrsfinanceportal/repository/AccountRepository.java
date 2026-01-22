package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUser(User user);
}
