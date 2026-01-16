package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import java.math.BigDecimal;

public class AccountBalanceView {

    private Long accountId;
    private BigDecimal currentAmount;

    public AccountBalanceView(Long accountId, BigDecimal currentAmount) {
        this.accountId = accountId;
        this.currentAmount = currentAmount;
    }

    public static AccountBalanceView from(Balance balance) {
        return new AccountBalanceView(
                balance.getAccount().getId(),
                balance.getAmount()
        );
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }
}
