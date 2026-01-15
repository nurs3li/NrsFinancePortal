package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    // 🔹 TEK constructor (Spring buradan inject eder)
    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    // 🔹 USER için (mevcut user'a account)
    public Account createAccount(User user, AccountType type) {
        Account account = Account.create(type, user);
        return accountRepository.save(account);
    }

    // 🔹 FINANCE_MANAGER için (başkasına account)
    public Account createAccountForUser(Long userId, AccountType type) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Account account = Account.create(type, user);
        return accountRepository.save(account);
    }

    // 🔹 USER kendi account’larını görür
    public List<Account> getAccountsOf(User user) {
        return accountRepository.findByUser(user);
    }
}
