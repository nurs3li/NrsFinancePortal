package com.nurseli.nrsfinanceportal.domain.fund;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fund_requests")
public class FundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private FundRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FundRequestStatus status;

    @Column(name = "amount", nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "request_note", length = 500)
    private String requestNote;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "bank_account_iban", length = 50)
    private String bankAccountIban;

    @Column(name = "receipt_file_url", length = 500)
    private String receiptFileUrl;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(name = "source_bank_name", length = 200)
    private String sourceBankName;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FundRequest() {
    }

    public static FundRequest create(
            User user,
            Account account,
            FundRequestType type,
            BigDecimal amount,
            String currency,
            String requestNote,
            String bankAccountIban,
            String receiptFileUrl,
            String referenceNo,
            String sourceBankName
    ) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        FundRequest r = new FundRequest();
        r.user = user;
        r.account = account;
        r.type = type;
        r.status = FundRequestStatus.PENDING;
        r.amount = amount;
        r.currency = (currency == null || currency.isBlank()) ? "TRY" : currency;
        r.requestNote = requestNote;
        r.bankAccountIban = bankAccountIban;
        r.receiptFileUrl = receiptFileUrl;
        r.referenceNo = referenceNo;
        r.sourceBankName = sourceBankName;
        r.createdAt = Instant.now();
        r.updatedAt = Instant.now();
        return r;
    }

    public void approve(Long approvedByUserId, String reviewNote) {
        if (this.status != FundRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be approved");
        }
        this.status = FundRequestStatus.APPROVED;
        this.approvedByUserId = approvedByUserId;
        this.reviewNote = reviewNote;
        this.approvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reject(String reviewNote) {
        if (this.status != FundRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be rejected");
        }
        this.status = FundRequestStatus.REJECTED;
        this.reviewNote = reviewNote;
        this.rejectedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (this.status != FundRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be cancelled");
        }
        this.status = FundRequestStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Account getAccount() { return account; }
    public FundRequestType getType() { return type; }
    public FundRequestStatus getStatus() { return status; }
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
    public Instant getUpdatedAt() { return updatedAt; }
}