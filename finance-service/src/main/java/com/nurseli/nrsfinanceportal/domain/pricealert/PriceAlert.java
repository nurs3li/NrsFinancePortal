package com.nurseli.nrsfinanceportal.domain.pricealert;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_alert")
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 16)
    private AssetType assetType;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 24)
    private PriceAlertConditionType conditionType;

    @Column(name = "threshold", nullable = false, precision = 20, scale = 8)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_window", length = 16)
    private PriceAlertChangeWindow changeWindow;

    @Enumerated(EnumType.STRING)
    @Column(name = "channels", nullable = false, length = 16)
    private PriceAlertChannels channels = PriceAlertChannels.BOTH;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PriceAlertStatus status = PriceAlertStatus.ACTIVE;

    @Column(name = "repeat_alert", nullable = false)
    private boolean repeatAlert = true;

    @Column(name = "cooldown_hours", nullable = false)
    private int cooldownHours = 24;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PriceAlert() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public PriceAlertConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(PriceAlertConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public PriceAlertChangeWindow getChangeWindow() {
        return changeWindow;
    }

    public void setChangeWindow(PriceAlertChangeWindow changeWindow) {
        this.changeWindow = changeWindow;
    }

    public PriceAlertChannels getChannels() {
        return channels;
    }

    public void setChannels(PriceAlertChannels channels) {
        this.channels = channels;
    }

    public PriceAlertStatus getStatus() {
        return status;
    }

    public void setStatus(PriceAlertStatus status) {
        this.status = status;
    }

    public boolean isRepeatAlert() {
        return repeatAlert;
    }

    public void setRepeatAlert(boolean repeatAlert) {
        this.repeatAlert = repeatAlert;
    }

    public int getCooldownHours() {
        return cooldownHours;
    }

    public void setCooldownHours(int cooldownHours) {
        this.cooldownHours = cooldownHours;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(Instant lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
