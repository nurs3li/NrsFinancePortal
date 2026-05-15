package com.nurseli.marketdata.domain.viop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mds_viop_price_history")
public class ViopPriceHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_code", nullable = false, length = 64)
    private String contractCode;

    @Column(nullable = false, length = 64)
    private String underlying;

    @Column(name = "asset_class", nullable = false, length = 32)
    private String assetClass;

    @Column(nullable = false, length = 64)
    private String segment;

    @Column(name = "price_time", nullable = false)
    private LocalDateTime priceTime;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal price;

    @Column(name = "period_minutes", nullable = false)
    private int periodMinutes;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "provider_timestamp")
    private LocalDateTime providerTimestamp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public String getContractCode() {
        return contractCode;
    }

    public void setContractCode(String contractCode) {
        this.contractCode = contractCode;
    }

    public String getUnderlying() {
        return underlying;
    }

    public void setUnderlying(String underlying) {
        this.underlying = underlying;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    public LocalDateTime getPriceTime() {
        return priceTime;
    }

    public void setPriceTime(LocalDateTime priceTime) {
        this.priceTime = priceTime;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getPeriodMinutes() {
        return periodMinutes;
    }

    public void setPeriodMinutes(int periodMinutes) {
        this.periodMinutes = periodMinutes;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getProviderTimestamp() {
        return providerTimestamp;
    }

    public void setProviderTimestamp(LocalDateTime providerTimestamp) {
        this.providerTimestamp = providerTimestamp;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
