package com.nurseli.nrsfinanceportal.api.dto.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ViopCloseReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Manuel VIOP pozisyonu kapatma request'i; kapanış fiyatı, tarih ve gerekçe bilgisini taşır.
 */
@Data
public class ManualViopPositionCloseRequest {

    @NotNull
    @Positive
    private BigDecimal closePrice;

    @NotNull
    private LocalDate closeDate;

    @PositiveOrZero
    private BigDecimal fee;

    private ViopCloseReason closeReason;

    private String note;
}
