package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;

import java.math.BigDecimal;

public class AccountBalanceView {

    private final Long accountId;
    private final BigDecimal currentAmount;

    public AccountBalanceView(Long accountId, BigDecimal currentAmount) {
        this.accountId = accountId;
        this.currentAmount = currentAmount;
    }

    /**
     * Transaction sonrası bakiye view
     */
    public static AccountBalanceView from(Transaction tx) {
        return new AccountBalanceView(
                tx.getAccount().getId(),
                tx.getBalanceAfter()
        );
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }
}
