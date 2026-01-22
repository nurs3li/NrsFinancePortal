package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionView {

    private final Long id;
    private final Long accountId;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final String type;
    private final LocalDateTime createdAt;

    public TransactionView(
            Long id,
            Long accountId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String type,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.type = type;
        this.createdAt = createdAt;
    }

    public static TransactionView from(Transaction transaction) {
        return new TransactionView(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getType().name(),
                transaction.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getType() { return type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
