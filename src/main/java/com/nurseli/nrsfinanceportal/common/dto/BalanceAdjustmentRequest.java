package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class BalanceAdjustmentRequest {

    @NotNull
    private Long accountId;

    @NotNull
    private BigDecimal amount;

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
