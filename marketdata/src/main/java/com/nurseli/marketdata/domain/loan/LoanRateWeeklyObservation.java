package com.nurseli.marketdata.domain.loan;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_rate_weekly_observation")
public class LoanRateWeeklyObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "series_code", nullable = false, length = 48)
    private String seriesCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "sub_type", nullable = false, length = 32)
    private LoanRateSubtype subType;

    @Column(name = "observed_date", nullable = false)
    private LocalDate observedDate;

    @Column(name = "rate_percent", nullable = false, precision = 19, scale = 6)
    private BigDecimal ratePercent;

    @Column(nullable = false, length = 16)
    private String frequency = "WEEKLY";

    @Column(nullable = false, length = 16)
    private String unit = "PERCENT";

    @Column(nullable = false, length = 16)
    private String source = "EVDS";

    @Column(nullable = false, length = 32)
    private String category = "LOAN_RATE";

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

    public String getSeriesCode() {
        return seriesCode;
    }

    public void setSeriesCode(String seriesCode) {
        this.seriesCode = seriesCode;
    }

    public LoanRateSubtype getSubType() {
        return subType;
    }

    public void setSubType(LoanRateSubtype subType) {
        this.subType = subType;
    }

    public LocalDate getObservedDate() {
        return observedDate;
    }

    public void setObservedDate(LocalDate observedDate) {
        this.observedDate = observedDate;
    }

    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    public void setRatePercent(BigDecimal ratePercent) {
        this.ratePercent = ratePercent;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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
