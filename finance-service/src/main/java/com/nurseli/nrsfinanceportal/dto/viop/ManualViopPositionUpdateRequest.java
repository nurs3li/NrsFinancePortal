package com.nurseli.nrsfinanceportal.dto.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ViopCategory;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ManualViopPositionUpdateRequest {

    @NotBlank
    private String symbol;

    private String displayName;

    @NotNull
    private ViopCategory viopCategory;

    private String underlyingSymbol;

    @NotNull
    private ViopDirection direction;

    @NotNull
    @Positive
    private BigDecimal contractCount;

    @NotNull
    @Positive
    private BigDecimal entryPrice;

    @NotNull
    private LocalDate entryDate;

    private BigDecimal currentPrice;

    @NotNull
    @Positive
    private BigDecimal contractMultiplier;

    private BigDecimal initialMargin;

    private LocalDate expiryDate;

    private String note;
}
