package com.nurseli.nrsfinanceportal.domain.transaction;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Account account;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private LocalDateTime createdAt;

    protected Transaction() {}

    private Transaction(Account account, BigDecimal amount, TransactionType type) {
        this.account = account;
        this.amount = amount;
        this.type = type;
        this.createdAt = LocalDateTime.now();
    }

    public static Transaction deposit(Account account, BigDecimal amount) {
        return new Transaction(account, amount, TransactionType.DEPOSIT);
    }
}
