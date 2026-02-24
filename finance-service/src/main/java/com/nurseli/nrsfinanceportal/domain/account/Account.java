package com.nurseli.nrsfinanceportal.domain.account;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "frozen_reason", length = 500)
    private String frozenReason;

    protected Account() {
        // JPA
    }

    private Account(AccountType type, User user) {
        this.type = type;
        this.user = user;
    }

    public static Account create(AccountType type, User user) {
        return new Account(type, user);
    }

    public Long getId() {
        return id;
    }

    public AccountType getType() {
        return type;
    }

    public User getUser() {
        return user;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getFrozenAt() {
        return frozenAt;
    }

    public String getFrozenReason() {
        return frozenReason;
    }

    public boolean isFrozen() {
        return status == AccountStatus.FROZEN;
    }

    public void freeze(Instant at, String reason) {
        this.status = AccountStatus.FROZEN;
        this.frozenAt = at;
        this.frozenReason = reason;
    }

    public void unfreeze() {
        this.status = AccountStatus.ACTIVE;
        this.frozenAt = null;
        this.frozenReason = null;
    }
}