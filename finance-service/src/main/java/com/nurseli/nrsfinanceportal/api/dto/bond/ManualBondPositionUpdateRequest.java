package com.nurseli.nrsfinanceportal.api.dto.bond;

import com.nurseli.nrsfinanceportal.domain.bond.BondType;
import com.nurseli.nrsfinanceportal.domain.bond.CouponFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Manuel bond pozisyonu güncelleme request'i; sembol, nominal, fiyat ve vade/kupon alanlarını taşır.
 */
@Data
public class ManualBondPositionUpdateRequest {

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
