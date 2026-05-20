package com.nurseli.marketdata.domain.debt;

import jakarta.persistence.*;

@Entity
@Table(name = "debt_instrument")
public class DebtInstrument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String isin;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 128)
    private String issuer;

    @Column(nullable = false, length = 16)
    private String maturityDate;

    @Column(name = "coupon_frequency_per_year")
    private Integer couponFrequencyPerYear;

    @Column(name = "coupon_frequency_label", length = 32)
    private String couponFrequencyLabel;

    @Column(name = "coupon_frequency_source", length = 48)
    private String couponFrequencySource;

    @Column(name = "evds_series_reference", length = 96)
    private String evdsSeriesReference;

    public Long getId() { return id; }
    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getMaturityDate() { return maturityDate; }
    public void setMaturityDate(String maturityDate) { this.maturityDate = maturityDate; }
    public Integer getCouponFrequencyPerYear() { return couponFrequencyPerYear; }
    public void setCouponFrequencyPerYear(Integer couponFrequencyPerYear) {
        this.couponFrequencyPerYear = couponFrequencyPerYear;
    }
    public String getCouponFrequencyLabel() { return couponFrequencyLabel; }
    public void setCouponFrequencyLabel(String couponFrequencyLabel) {
        this.couponFrequencyLabel = couponFrequencyLabel;
    }
    public String getCouponFrequencySource() { return couponFrequencySource; }
    public void setCouponFrequencySource(String couponFrequencySource) {
        this.couponFrequencySource = couponFrequencySource;
    }
    public String getEvdsSeriesReference() { return evdsSeriesReference; }
    public void setEvdsSeriesReference(String evdsSeriesReference) {
        this.evdsSeriesReference = evdsSeriesReference;
    }
}
