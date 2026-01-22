package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.NotNull;

public class TransactionReversalRequest {

    @NotNull
    private Long transactionId;

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }
}
