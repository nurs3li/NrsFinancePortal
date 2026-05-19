package com.nurseli.nrsfinanceportal.dto.viop;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ManualViopPositionCloseRequest {

    @NotNull
    @Positive
    private BigDecimal closePrice;

    @NotNull
    private LocalDate closeDate;
}
