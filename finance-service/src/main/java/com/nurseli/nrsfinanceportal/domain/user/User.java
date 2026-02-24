package com.nurseli.nrsfinanceportal.domain.user;

import com.nurseli.nrsfinanceportal.domain.whale.WhaleLevel;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_keycloak_id",
                        columnNames = "keycloak_user_id"
                )
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Immutable identity reference from Keycloak (JWT sub)
     */
    @Column(name = "keycloak_user_id", nullable = false, updatable = false)
    private String keycloakUserId;

    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ===================== 🐋 WHALE FIELDS =====================

    @Column(name = "is_whale", nullable = false)
    private boolean whale;

    @Enumerated(EnumType.STRING)
    @Column(name = "whale_level")
    private WhaleLevel whaleLevel;

    @Column(name = "whale_since")
    private Instant whaleSince;

    // ===================== JPA =====================

    protected User() {
        // JPA only
    }

    private User(String keycloakUserId,
                 String username,
                 String email,
                 Role role) {

        this.keycloakUserId = keycloakUserId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.createdAt = Instant.now();

        // defaults
        this.whale = false;
        this.whaleLevel = WhaleLevel.NONE;
    }
    /**
     * Factory for first-login (varsayılan USER).
     */
    public static User createFromIdentity(String keycloakUserId,
                                          String email,
                                          String username) {
        return createFromIdentity(keycloakUserId, email, username, Role.USER);
    }

    /**
     * Factory for first-login with role from Keycloak JWT.
     */
    public static User createFromIdentity(String keycloakUserId,
                                          String email,
                                          String username,
                                          Role role) {
        return new User(
                keycloakUserId,
                username,
                email,
                role != null ? role : Role.USER
        );
    }

    // ===================== 🧠 DOMAIN BEHAVIOR =====================

    /**
     * Whale status updater (called from WhaleAlert consumer)
     */
    public void markAsWhale(WhaleLevel level, Instant triggeredAt) {
        this.whale = true;
        this.whaleLevel = level;
        this.whaleSince = triggeredAt;
    }


    public void clearWhaleStatus() {
        this.whale = false;
        this.whaleLevel = WhaleLevel.NONE;
        this.whaleSince = null;
    }
    public void setRole(Role role) {
        if (role != null) {
            this.role = role;
        }
    }
    // ===================== GETTERS =====================

    public Long getId() {
        return id;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isWhale() {
        return whale;
    }

    public WhaleLevel getWhaleLevel() {
        return whaleLevel;
    }

    public Instant getWhaleSince() {
        return whaleSince;
    }
}
