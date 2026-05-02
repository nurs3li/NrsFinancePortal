package com.nurseli.marketdata.domain.derivatives;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "open_interest_snapshot")
public class OpenInterestSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String contractCode;

    @Column(nullable = false)
    private Long openInterest;

    @Column
    private Long dailyVolume;

    @Column(nullable = false)
    private LocalDateTime asOf;

    public Long getId() { return id; }
    public String getContractCode() { return contractCode; }
    public void setContractCode(String contractCode) { this.contractCode = contractCode; }
    public Long getOpenInterest() { return openInterest; }
    public void setOpenInterest(Long openInterest) { this.openInterest = openInterest; }
    public Long getDailyVolume() { return dailyVolume; }
    public void setDailyVolume(Long dailyVolume) { this.dailyVolume = dailyVolume; }
    public LocalDateTime getAsOf() { return asOf; }
    public void setAsOf(LocalDateTime asOf) { this.asOf = asOf; }
}
