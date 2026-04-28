package com.nurseli.marketdata.domain.debt;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "debt_snapshot")
public class DebtSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String isin;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal dirtyPrice;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal yieldPct;

    @Column(nullable = false, length = 24)
    private String source;

    @Column(nullable = false)
    private LocalDateTime asOf;

    public Long getId() { return id; }
    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }
    public BigDecimal getDirtyPrice() { return dirtyPrice; }
    public void setDirtyPrice(BigDecimal dirtyPrice) { this.dirtyPrice = dirtyPrice; }
    public BigDecimal getYieldPct() { return yieldPct; }
    public void setYieldPct(BigDecimal yieldPct) { this.yieldPct = yieldPct; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getAsOf() { return asOf; }
    public void setAsOf(LocalDateTime asOf) { this.asOf = asOf; }
}
