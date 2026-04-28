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
    public LocalDateTime getAsOf() { return asOf; }
    public void setAsOf(LocalDateTime asOf) { this.asOf = asOf; }
}
