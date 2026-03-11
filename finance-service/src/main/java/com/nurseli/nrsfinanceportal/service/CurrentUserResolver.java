package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.identity.JwtIdentityReader;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.user.Role;
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

    @Transactional
    public User getOrCreateCurrentUser() {

        String keycloakUserId = jwtIdentityReader.getRequiredSubject();
        String email = jwtIdentityReader.getEmail();
        String username = jwtIdentityReader.getUsername();
        Role jwtRole = jwtIdentityReader.getRealmRole();
        Boolean emailVerified = jwtIdentityReader.getEmailVerified();

        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseGet(() -> createNewUser(keycloakUserId, email, username, jwtRole));

        boolean changed = false;

        if (user.getRole() != jwtRole) {
            user.setRole(jwtRole);
            changed = true;
        }

        if (emailVerified != null) {
            boolean before = user.isEmailVerified();
            user.setEmailVerified(emailVerified);
            if (before != user.isEmailVerified()) {
                changed = true;
            }
        }

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }

        return user;
    }

    private User createNewUser(String keycloakUserId, String email, String username, Role role) {

        User user = userRepository.save(
                User.createFromIdentity(keycloakUserId, email, username, role)
        );

        Account cash = accountRepository.save(
                Account.create(AccountType.CASH, user)
        );
        balanceRepository.save(Balance.zero(cash));

        Account demo = accountRepository.save(
                Account.create(AccountType.DEMO, user)
        );
        balanceRepository.save(
                Balance.of(demo, new BigDecimal("1000000"))
        );

        return user;
    }

    public Long getCurrentUserId() {
        return getOrCreateCurrentUser().getId();
    }
}