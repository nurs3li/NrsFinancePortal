package com.nurseli.nrsfinanceportal.dto.bond;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
}
