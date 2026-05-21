package com.nurseli.nrsfinanceportal.domain.user;

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

    @Column(name = "keycloak_user_id", nullable = false, updatable = false)
    private String keycloakUserId;

    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Giriş (Keycloak) seviyesinde askıya alındı; mevcut JWT oturumlarını finance API'de keser.
     */
    @Column(name = "login_suspended", nullable = false)
    private boolean loginSuspended;

    @Column(name = "login_suspended_at")
    private Instant loginSuspendedAt;

    @Column(name = "login_suspended_reason", length = 500)
    private String loginSuspendedReason;

    protected User() {
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
        this.emailVerified = false;
        this.loginSuspended = false;
    }

    public static User createFromIdentity(String keycloakUserId,
                                          String email,
                                          String username) {
        return createFromIdentity(keycloakUserId, email, username, Role.USER);
    }

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

    public void suspendLogin(Instant at, String reason) {
        this.loginSuspended = true;
        this.loginSuspendedAt = at;
        this.loginSuspendedReason = reason != null && !reason.isBlank() ? reason : null;
    }

    public void unsuspendLogin() {
        this.loginSuspended = false;
        this.loginSuspendedAt = null;
        this.loginSuspendedReason = null;
    }

    public boolean isLoginSuspended() {
        return loginSuspended;
    }

    public Instant getLoginSuspendedAt() {
        return loginSuspendedAt;
    }

    public String getLoginSuspendedReason() {
        return loginSuspendedReason;
    }

    public void setRole(Role role) {
        if (role != null) {
            this.role = role;
        }
    }

    public void setEmailVerified(Boolean verified) {
        if (verified != null) {
            this.emailVerified = verified;
        }
    }

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

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setEmail(String email) {
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
    }

    public void setUsername(String username) {
        if (username != null && !username.isBlank()) {
            this.username = username;
        }
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName != null && firstName.isBlank() ? null : firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName != null && lastName.isBlank() ? null : lastName;
    }
}
