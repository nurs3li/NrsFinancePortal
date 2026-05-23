package com.nurseli.nrsfinanceportal.domain.portfolio.ai;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * AI analiz e-posta teslimat tercihleri entity; sıklık ve son gönderim zamanı.
 */
@Entity
@Table(name = "portfolio_ai_email_delivery")
public class PortfolioAiEmailDeliveryEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", length = 16)
    private PortfolioAiEmailDeliveryFrequency frequency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PortfolioAiEmailDeliveryEntity() {
    }

    public PortfolioAiEmailDeliveryEntity(
            User user,
            boolean enabled,
            String email,
            PortfolioAiEmailDeliveryFrequency frequency,
            Instant updatedAt
    ) {
        this.user = user;
        this.userId = user.getId();
        this.enabled = enabled;
        this.email = email;
        this.frequency = frequency;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEmail() {
        return email;
    }

    public PortfolioAiEmailDeliveryFrequency getFrequency() {
        return frequency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(boolean enabled, String email, PortfolioAiEmailDeliveryFrequency frequency, Instant updatedAt) {
        this.enabled = enabled;
        this.email = email;
        this.frequency = frequency;
        this.updatedAt = updatedAt;
    }
}
