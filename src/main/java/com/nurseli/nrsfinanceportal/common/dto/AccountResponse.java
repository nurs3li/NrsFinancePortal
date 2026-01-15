package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;

public class AccountResponse {

    private Long id;
    private AccountType type;

    public AccountResponse(Long id, AccountType type) {
        this.id = id;
        this.type = type;
    }

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getType()
        );
    }

    public Long getId() {
        return id;
    }

    public AccountType getType() {
        return type;
    }
}
