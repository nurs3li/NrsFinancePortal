package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class BalanceAdjustmentRequest {

    @NotNull
    private Long accountId;

    @NotNull
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
