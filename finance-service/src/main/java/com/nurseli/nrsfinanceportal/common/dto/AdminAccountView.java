package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountStatus;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;

import java.time.Instant;

public record AdminAccountView(
        Long id,
        Long userId,
        String userEmail,
        String username,
        AccountType type,
        AccountStatus status,
        Instant frozenAt,
        String frozenReason
) {
    public static AdminAccountView from(Account a) {
        return new AdminAccountView(
                a.getId(),
                a.getUser().getId(),
                a.getUser().getEmail(),
                a.getUser().getUsername(),
                a.getType(),
                a.getStatus(),
                a.getFrozenAt(),
                a.getFrozenReason()
        );
    }
}