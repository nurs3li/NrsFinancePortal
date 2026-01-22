package com.nurseli.nrsfinanceportal.domain.transaction;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Account account;

    @ManyToOne(optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal balanceAfter;

    /**
     * Bu transaction bir reversal ise,
     * hangi transaction'ı reverse ettiğini tutar.
     *
     * ❗ UNIQUE YOK
     * ❗ OneToOne YOK
     */
    @ManyToOne
    @JoinColumn(name = "reversed_transaction_id")
    private Transaction reversedTransaction;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Transaction() {}

    /* ================= FACTORY METHODS ================= */

    public static Transaction deposit(
            Account account,
            User user,
            BigDecimal amount,
            BigDecimal balanceAfter
    ) {
        return create(account, user, amount, balanceAfter, TransactionType.DEPOSIT, null);
    }

    public static Transaction withdraw(
            Account account,
            User user,
            BigDecimal amount,
            BigDecimal balanceAfter
    ) {
        return create(account, user, amount, balanceAfter, TransactionType.WITHDRAW, null);
    }

    public static Transaction reversal(
            Transaction original,
            User user,
            BigDecimal balanceAfter
    ) {
        return create(
                original.account,
                user,
                original.amount,
                balanceAfter,
                TransactionType.REVERSAL,
                original
        );
    }

    private static Transaction create(
            Account account,
            User user,
            BigDecimal amount,
            BigDecimal balanceAfter,
            TransactionType type,
            Transaction reversedTransaction
    ) {
        Transaction tx = new Transaction();
        tx.account = account;
        tx.user = user;
        tx.amount = amount;
        tx.balanceAfter = balanceAfter;
        tx.type = type;
        tx.reversedTransaction = reversedTransaction;
        tx.createdAt = LocalDateTime.now();
        return tx;
    }

    /* ================= GETTERS ================= */

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public User getUser() { return user; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public TransactionType getType() { return type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Transaction getReversedTransaction() { return reversedTransaction; }
}
