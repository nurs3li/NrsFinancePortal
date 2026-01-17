package com.nurseli.nrsfinanceportal.domain.transaction;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Transaction() {
        // JPA
    }

    private Transaction(Account account, BigDecimal amount, TransactionType type) {
        this.account = account;
        this.amount = amount;
        this.type = type;
        this.createdAt = LocalDateTime.now();
    }
    public static Transaction withdraw(Account account, BigDecimal amount) {
        return new Transaction(account, amount, TransactionType.WITHDRAW);
    }

    /* FACTORY */
    public static Transaction deposit(Account account, BigDecimal amount) {
        return new Transaction(account, amount, TransactionType.DEPOSIT);
    }

    /* GETTERS */
    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
