package com.nurseli.nrsfinanceportal.dto.bond;

import com.nurseli.nrsfinanceportal.domain.bond.BondCloseType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ManualBondPositionSellRequest {

    @NotNull
    @Positive
    private BigDecimal sellPrice;

    @NotNull
    private LocalDate sellDate;

    private BondCloseType closeType;

    @PositiveOrZero
    private BigDecimal collectedCouponAmount;

    @PositiveOrZero
    private BigDecimal fee;

    private String note;
}
