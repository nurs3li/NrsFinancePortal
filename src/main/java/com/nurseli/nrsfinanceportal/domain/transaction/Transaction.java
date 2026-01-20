package com.nurseli.nrsfinanceportal.domain.transaction;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import jakarta.persistence.*;
import com.nurseli.nrsfinanceportal.domain.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_tx_account", columnList = "account_id"),
                @Index(name = "idx_tx_user", columnList = "user_id"),
                @Index(name = "idx_tx_created_at", columnList = "created_at")
        }
)

public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Transaction() {}

    private Transaction(Account account, User user,
                        BigDecimal amount,
                        BigDecimal balanceAfter,
                        TransactionType type) {
        this.account = account;
        this.user = user;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.type = type;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Transaction deposit(Account account, User user,
                                      BigDecimal amount,
                                      BigDecimal balanceAfter) {
        return new Transaction(account, user, amount, balanceAfter, TransactionType.DEPOSIT);
    }

    public static Transaction withdraw(Account account, User user,
                                       BigDecimal amount,
                                       BigDecimal balanceAfter) {
        return new Transaction(account, user, amount, balanceAfter, TransactionType.WITHDRAW);
    }

    // getters
    public User getUser() {
        return user;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

