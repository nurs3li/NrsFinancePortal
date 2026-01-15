package com.nurseli.nrsfinanceportal.domain.account;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

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
}
