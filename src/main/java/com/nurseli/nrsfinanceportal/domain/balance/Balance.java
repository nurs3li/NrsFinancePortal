package com.nurseli.nrsfinanceportal.domain.balance;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "balances")
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(nullable = false)
    private BigDecimal amount;

    protected Balance() {
        // JPA
    }

    private Balance(Account account) {
        this.account = account;
        this.amount = BigDecimal.ZERO;
    }

    public static Balance createFor(Account account) {
        return new Balance(account);
    }

    public void increase(BigDecimal value) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Increase amount must be positive");
        }
        this.amount = this.amount.add(value);
    }

    public void decrease(BigDecimal value) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Withdraw amount must be positive");
        }
        if (this.amount.compareTo(value) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        this.amount = this.amount.subtract(value);
    }


    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Account getAccount() {
        return account;
    }
}
