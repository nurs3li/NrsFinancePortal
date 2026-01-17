package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionView {

    private Long id;
    private BigDecimal amount;
    private TransactionType type;
    private LocalDateTime createdAt;

    public TransactionView(
            Long id,
            BigDecimal amount,
            TransactionType type,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.amount = amount;
        this.type = type;
        this.createdAt = createdAt;
    }

    public static TransactionView from(Transaction transaction) {
        return new TransactionView(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
