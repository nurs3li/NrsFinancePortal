package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;

/**
 * TCMB politika faizi (EVDS: aylık, birim yüzde). Fonlama maliyeti serilerinden ayrıdır.
 * {@code latestDate} gözlem dönemidir ({@code yyyy-MM}).
 */
public record PolicyRateTrResponse(
        String logicalIndicatorCode,
        String seriesCode,
        BigDecimal valuePercent,
        String latestDate,
        String frequency,
        String unit,
        String displayLabel
) {}
