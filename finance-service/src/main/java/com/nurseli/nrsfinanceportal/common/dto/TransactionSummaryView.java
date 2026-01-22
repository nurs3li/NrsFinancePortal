package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

public class TransactionSummaryView {

    private final BigDecimal totalAmount;
    private final BigDecimal totalCount;

    private TransactionSummaryView(
            BigDecimal totalAmount,
            BigDecimal totalCount
    ) {
        this.totalAmount = totalAmount;
        this.totalCount = totalCount;
    }

    public static TransactionSummaryView of(
            BigDecimal totalAmount,
            BigDecimal totalCount
    ) {
        return new TransactionSummaryView(totalAmount, totalCount);
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getTotalCount() {
        return totalCount;
    }
}
