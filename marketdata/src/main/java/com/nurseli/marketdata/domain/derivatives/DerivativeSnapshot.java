package com.nurseli.marketdata.domain.derivatives;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "derivative_snapshot")
public class DerivativeSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String contractCode;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal theoreticalSpot;

    @Column(nullable = false, length = 24)
    private String source;

    @Column(precision = 19, scale = 6)
    private BigDecimal basis;

    @Column(precision = 19, scale = 6)
    private BigDecimal maintenanceMargin;

    @Column
    private Integer daysToExpiry;

    @Column(length = 16)
    private String dataQuality;

    @Column(length = 32)
    private String priceSource;

    @Column
    private Long priceLatencyMs;

    @Column(nullable = false)
    private LocalDateTime asOf;

    public Long getId() { return id; }
    public String getContractCode() { return contractCode; }
    public void setContractCode(String contractCode) { this.contractCode = contractCode; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getTheoreticalSpot() { return theoreticalSpot; }
    public void setTheoreticalSpot(BigDecimal theoreticalSpot) { this.theoreticalSpot = theoreticalSpot; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public BigDecimal getBasis() { return basis; }
    public void setBasis(BigDecimal basis) { this.basis = basis; }
    public BigDecimal getMaintenanceMargin() { return maintenanceMargin; }
    public void setMaintenanceMargin(BigDecimal maintenanceMargin) { this.maintenanceMargin = maintenanceMargin; }
    public Integer getDaysToExpiry() { return daysToExpiry; }
    public void setDaysToExpiry(Integer daysToExpiry) { this.daysToExpiry = daysToExpiry; }
    public String getDataQuality() { return dataQuality; }
    public void setDataQuality(String dataQuality) { this.dataQuality = dataQuality; }
    public String getPriceSource() { return priceSource; }
    public void setPriceSource(String priceSource) { this.priceSource = priceSource; }
    public Long getPriceLatencyMs() { return priceLatencyMs; }
    public void setPriceLatencyMs(Long priceLatencyMs) { this.priceLatencyMs = priceLatencyMs; }
    public LocalDateTime getAsOf() { return asOf; }
    public void setAsOf(LocalDateTime asOf) { this.asOf = asOf; }
}
