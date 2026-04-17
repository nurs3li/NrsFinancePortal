package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.fund.FundRequest;

import java.math.BigDecimal;
import java.time.Instant;

public class FundRequestView {

    private final Long id;
    private final Long userId;
    private final Long accountId;
    private final String type;
    private final String status;
    private final BigDecimal amount;
    private final String currency;
    private final String requestNote;
    private final String reviewNote;
    private final String bankAccountIban;
    private final String receiptFileUrl;
    private final String referenceNo;
    private final String sourceBankName;
    private final Long approvedByUserId;
    private final Instant approvedAt;
    private final Instant rejectedAt;
    private final Instant createdAt;

    public FundRequestView(
            Long id,
            Long userId,
            Long accountId,
            String type,
            String status,
            BigDecimal amount,
            String currency,
            String requestNote,
            String reviewNote,
            String bankAccountIban,
            String receiptFileUrl,
            String referenceNo,
            String sourceBankName,
            Long approvedByUserId,
            Instant approvedAt,
            Instant rejectedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.requestNote = requestNote;
        this.reviewNote = reviewNote;
        this.bankAccountIban = bankAccountIban;
        this.receiptFileUrl = receiptFileUrl;
        this.referenceNo = referenceNo;
        this.sourceBankName = sourceBankName;
        this.approvedByUserId = approvedByUserId;
        this.approvedAt = approvedAt;
        this.rejectedAt = rejectedAt;
        this.createdAt = createdAt;
    }

    public static FundRequestView from(FundRequest r) {
        return new FundRequestView(
                r.getId(),
                r.getUser().getId(),
                r.getAccount().getId(),
                r.getType().name(),
                r.getStatus().name(),
                r.getAmount(),
                r.getCurrency(),
                r.getRequestNote(),
                r.getReviewNote(),
                r.getBankAccountIban(),
                r.getReceiptFileUrl(),
                r.getReferenceNo(),
                r.getSourceBankName(),
                r.getApprovedByUserId(),
                r.getApprovedAt(),
                r.getRejectedAt(),
                r.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAccountId() { return accountId; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getRequestNote() { return requestNote; }
    public String getReviewNote() { return reviewNote; }
    public String getBankAccountIban() { return bankAccountIban; }
    public String getReceiptFileUrl() { return receiptFileUrl; }
    public String getReferenceNo() { return referenceNo; }
    public String getSourceBankName() { return sourceBankName; }
    public Long getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Instant getCreatedAt() { return createdAt; }
}