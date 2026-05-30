package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "manual_portfolio_read_snapshot")
public class ManualPortfolioReadSnapshot {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "positions_fingerprint", nullable = false, length = 64)
    private String positionsFingerprint;

    @Column(name = "views_json", columnDefinition = "TEXT")
    private String viewsJson;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "insights_json", columnDefinition = "TEXT")
    private String insightsJson;

    @Column(name = "market_pricing_at")
    private Instant marketPricingAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "warm_status", nullable = false, length = 16)
    private ManualPortfolioWarmStatus warmStatus;

    @Column(name = "warmed_at")
    private Instant warmedAt;

    @Column(name = "warm_error", length = 500)
    private String warmError;

    protected ManualPortfolioReadSnapshot() {
    }

    public static ManualPortfolioReadSnapshot forUser(User user) {
        ManualPortfolioReadSnapshot row = new ManualPortfolioReadSnapshot();
        row.user = user;
        row.warmStatus = ManualPortfolioWarmStatus.PENDING;
        return row;
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public String getPositionsFingerprint() {
        return positionsFingerprint;
    }

    public void setPositionsFingerprint(String positionsFingerprint) {
        this.positionsFingerprint = positionsFingerprint;
    }

    public String getViewsJson() {
        return viewsJson;
    }

    public void setViewsJson(String viewsJson) {
        this.viewsJson = viewsJson;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getInsightsJson() {
        return insightsJson;
    }

    public void setInsightsJson(String insightsJson) {
        this.insightsJson = insightsJson;
    }

    public Instant getMarketPricingAt() {
        return marketPricingAt;
    }

    public void setMarketPricingAt(Instant marketPricingAt) {
        this.marketPricingAt = marketPricingAt;
    }

    public ManualPortfolioWarmStatus getWarmStatus() {
        return warmStatus;
    }

    public void setWarmStatus(ManualPortfolioWarmStatus warmStatus) {
        this.warmStatus = warmStatus;
    }

    public Instant getWarmedAt() {
        return warmedAt;
    }

    public void setWarmedAt(Instant warmedAt) {
        this.warmedAt = warmedAt;
    }

    public String getWarmError() {
        return warmError;
    }

    public void setWarmError(String warmError) {
        this.warmError = warmError;
    }
}
