package com.nurseli.marketdata.domain.tefas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tefas_fund_profile")
@Getter
@Setter
@NoArgsConstructor
public class TefasFundProfile {

    @Id
    @Column(length = 10, nullable = false)
    private String code;

    @Column(length = 256)
    private String title;

    @Column(name = "fund_type", length = 128)
    private String fundType;

    @Column(name = "risk_level")
    private Integer riskLevel;

    @Column(name = "tefas_listed")
    private Boolean tefasListed;

    @Column(name = "return_1m")
    private Double return1m;

    @Column(name = "return_3m")
    private Double return3m;

    @Column(name = "return_6m")
    private Double return6m;

    @Column(name = "return_1y")
    private Double return1y;

    @Column(name = "return_ytd")
    private Double returnYtd;

    @Column(name = "return_3y")
    private Double return3y;

    @Column(name = "return_5y")
    private Double return5y;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
