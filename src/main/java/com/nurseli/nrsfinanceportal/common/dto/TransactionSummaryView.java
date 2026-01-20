package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.util.Objects;

public final class TransactionSummaryView {

    private final BigDecimal totalDeposit;
    private final BigDecimal totalWithdraw;
    private final BigDecimal netChange;

    private TransactionSummaryView(
            BigDecimal totalDeposit,
            BigDecimal totalWithdraw
    ) {
        this.totalDeposit = normalize(totalDeposit);
        this.totalWithdraw = normalize(totalWithdraw);
        this.netChange = this.totalDeposit.subtract(this.totalWithdraw);
    }

    public static TransactionSummaryView of(
            BigDecimal totalDeposit,
            BigDecimal totalWithdraw
    ) {
        return new TransactionSummaryView(totalDeposit, totalWithdraw);
    }

    private static BigDecimal normalize(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO);
    }

    public BigDecimal getTotalDeposit() {
        return totalDeposit;
    }

    public BigDecimal getTotalWithdraw() {
        return totalWithdraw;
    }

    public BigDecimal getNetChange() {
        return netChange;
    }
}
