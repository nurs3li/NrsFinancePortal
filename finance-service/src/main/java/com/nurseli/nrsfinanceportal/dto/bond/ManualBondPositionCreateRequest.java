package com.nurseli.nrsfinanceportal.dto.bond;

import com.nurseli.nrsfinanceportal.domain.bond.BondType;
import com.nurseli.nrsfinanceportal.domain.bond.CouponFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ManualBondPositionCreateRequest {

    @NotBlank
    private String symbol;

    private String displayName;

    @NotNull
    private BondType bondType;

    @NotBlank
    private String currency;

    @NotNull
    @Positive
    private BigDecimal nominalValue;

    @NotNull
    @Positive
    private BigDecimal buyPrice;

    @NotNull
    private LocalDate buyDate;

    private BigDecimal currentPrice;

    private LocalDate maturityDate;

    private BigDecimal couponRate;

    private CouponFrequency couponFrequency;

    private String note;
}
