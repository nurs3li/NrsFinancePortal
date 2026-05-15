package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TCMB ağırlıklı ortalama fonlama maliyeti (EVDS: haftalık, birim yüzde).
 * Politika faizi ({@code POLICY_RATE_TR}) ile karıştırılmamalıdır.
 */
public record TcmbWeightedFundingCostResponse(
        String logicalIndicatorCode,
        String seriesCode,
        BigDecimal valuePercent,
        LocalDateTime asOf,
        String frequency,
        String unit,
        String displayLabel,
        String description
) {}
