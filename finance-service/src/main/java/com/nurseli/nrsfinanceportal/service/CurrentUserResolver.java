package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.identity.JwtIdentityReader;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Service
public class CurrentUserResolver {

    private final JwtIdentityReader jwtIdentityReader;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;

    /**
     * Returns the current authenticated User.
     * If the user does not exist in DB, creates it (first login sync).
     */
    @Transactional
    public User getOrCreateCurrentUser() {

        String keycloakUserId = jwtIdentityReader.getRequiredSubject();
        String email = jwtIdentityReader.getEmail();
        String username = jwtIdentityReader.getUsername();

        return userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseGet(() -> {

                    // 1️⃣ USER
                    User user = userRepository.save(
                            User.createFromIdentity(
                                    keycloakUserId,
                                    email,
                                    username
                            )
                    );

                    // 2️⃣ CASH ACCOUNT
                    Account cash = accountRepository.save(
                            Account.create(AccountType.CASH, user)
                    );
                    balanceRepository.save(Balance.zero(cash));

// 3️⃣ DEMO ACCOUNT (🔥)
                    Account demo = accountRepository.save(
                            Account.create(AccountType.DEMO, user)
                    );

// 🔥 DEMO'ya 1.000.000 TRY
                    balanceRepository.save(
                            Balance.of(demo, new BigDecimal("1000000"))
                    );


                    return user;
                });
    }

    public Long getCurrentUserId() {
        return getOrCreateCurrentUser().getId();
    }
}
