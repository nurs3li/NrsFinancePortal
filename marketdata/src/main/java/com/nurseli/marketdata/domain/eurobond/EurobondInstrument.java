package com.nurseli.marketdata.domain.eurobond;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "eurobond_instrument")
public class EurobondInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String isin;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String issuer;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "coupon_pct", precision = 9, scale = 4)
    private BigDecimal couponPct;

    @Column(name = "maturity_date", nullable = false, length = 16)
    private String maturityDate;

    @Column(name = "evds_dirty_price_series", length = 96)
    private String evdsDirtyPriceSeries;

    @Column(name = "evds_yield_series", length = 96)
    private String evdsYieldSeries;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getCouponPct() {
        return couponPct;
    }

    public void setCouponPct(BigDecimal couponPct) {
        this.couponPct = couponPct;
    }

    public String getMaturityDate() {
        return maturityDate;
    }

    public void setMaturityDate(String maturityDate) {
        this.maturityDate = maturityDate;
    }

    public String getEvdsDirtyPriceSeries() {
        return evdsDirtyPriceSeries;
    }

    public void setEvdsDirtyPriceSeries(String evdsDirtyPriceSeries) {
        this.evdsDirtyPriceSeries = evdsDirtyPriceSeries;
    }

    public String getEvdsYieldSeries() {
        return evdsYieldSeries;
    }

    public void setEvdsYieldSeries(String evdsYieldSeries) {
        this.evdsYieldSeries = evdsYieldSeries;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
