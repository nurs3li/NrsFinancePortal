package com.nurseli.marketdata.domain.inflation;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inflation_index_monthly")
public class InflationIndexMonthlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 8)
    private InflationIndicatorType indicatorType;

    @Column(name = "series_code", nullable = false, length = 48)
    private String seriesCode;

    @Column(nullable = false, length = 32)
    private String category = "INFLATION";

    @Column(nullable = false, length = 16)
    private String source = "EVDS";

    @Column(nullable = false, length = 16)
    private String frequency = "MONTHLY";

    @Column(nullable = false, length = 16)
    private String unit = "INDEX";

    @Column(name = "base_year")
    private Integer baseYear;

    @Column(nullable = false, length = 4)
    private String country = "TR";

    @Column(name = "observation_month", nullable = false)
    private LocalDate observationMonth;

    @Column(name = "index_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal indexValue;

    @Column(name = "monthly_change_pct", precision = 19, scale = 6)
    private BigDecimal monthlyChangePct;

    @Column(name = "annual_change_pct", precision = 19, scale = 6)
    private BigDecimal annualChangePct;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public InflationIndicatorType getIndicatorType() {
        return indicatorType;
    }

    public void setIndicatorType(InflationIndicatorType indicatorType) {
        this.indicatorType = indicatorType;
    }

    public String getSeriesCode() {
        return seriesCode;
    }

    public void setSeriesCode(String seriesCode) {
        this.seriesCode = seriesCode;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getBaseYear() {
        return baseYear;
    }

    public void setBaseYear(Integer baseYear) {
        this.baseYear = baseYear;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public LocalDate getObservationMonth() {
        return observationMonth;
    }

    public void setObservationMonth(LocalDate observationMonth) {
        this.observationMonth = observationMonth;
    }

    public BigDecimal getIndexValue() {
        return indexValue;
    }

    public void setIndexValue(BigDecimal indexValue) {
        this.indexValue = indexValue;
    }

    public BigDecimal getMonthlyChangePct() {
        return monthlyChangePct;
    }

    public void setMonthlyChangePct(BigDecimal monthlyChangePct) {
        this.monthlyChangePct = monthlyChangePct;
    }

    public BigDecimal getAnnualChangePct() {
        return annualChangePct;
    }

    public void setAnnualChangePct(BigDecimal annualChangePct) {
        this.annualChangePct = annualChangePct;
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
