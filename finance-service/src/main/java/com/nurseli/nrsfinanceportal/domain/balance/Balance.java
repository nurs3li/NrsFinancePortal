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
    private Account account;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    protected Balance() {}

    public Balance(Account account) {
        this.account = account;
        this.amount = BigDecimal.ZERO;
    }

    // ✅ BUNU EKLE
    public static Balance zero(Account account) {
        return new Balance(account);
    }

    public BigDecimal increase(BigDecimal value) {
        this.amount = this.amount.add(value);
        return this.amount;
    }

    public BigDecimal decrease(BigDecimal value) {
        BigDecimal newAmount = this.amount.subtract(value);
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        this.amount = newAmount;
        return this.amount;
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public BigDecimal getAmount() { return amount; }
}
