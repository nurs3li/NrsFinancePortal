package com.nurseli.marketdata.domain.eurobond;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "eurobond_price_snapshot")
public class EurobondPriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String isin;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "clean_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal cleanPrice;

    @Column(name = "yield_pct", precision = 19, scale = 6)
    private BigDecimal yieldPct;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public BigDecimal getCleanPrice() {
        return cleanPrice;
    }

    public void setCleanPrice(BigDecimal cleanPrice) {
        this.cleanPrice = cleanPrice;
    }

    public BigDecimal getYieldPct() {
        return yieldPct;
    }

    public void setYieldPct(BigDecimal yieldPct) {
        this.yieldPct = yieldPct;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
