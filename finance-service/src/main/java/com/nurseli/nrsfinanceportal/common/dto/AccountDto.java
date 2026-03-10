package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountStatus;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;

import java.time.Instant;

public record AccountDto(
        Long id,
        AccountType type,
        AccountStatus status,
        Instant frozenAt,
        String frozenReason
) {
    public static AccountDto from(Account a) {
        if (a == null) return null;
        return new AccountDto(
                a.getId(),
                a.getType(),
                a.getStatus(),
                a.getFrozenAt(),
                a.getFrozenReason()
        );
    }
}