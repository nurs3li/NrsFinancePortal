package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import java.math.BigDecimal;

public class AccountBalanceView {

    private final Long accountId;
    private final BigDecimal currentAmount;

    public AccountBalanceView(Long accountId, BigDecimal currentAmount) {
        this.accountId = accountId;
        this.currentAmount = currentAmount;
    }

    public static AccountBalanceView from(Balance balance) {
        return new AccountBalanceView(
                balance.getAccount().getId(), // ✅ ARTIK ÇALIŞIR
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
