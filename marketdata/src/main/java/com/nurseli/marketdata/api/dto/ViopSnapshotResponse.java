package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ViopSnapshotResponse(
        String contractCode,
        String expiryDate,
        String contractMonth,
        BigDecimal price,
        BigDecimal theoreticalSpot,
        BigDecimal basis,
        BigDecimal annualizedBasisPct,
        BigDecimal marginRequirement,
        String longShortIndicator,
        Long openInterest,
        Long dailyVolume,
        String oiPriceRegime,
        String source,
        LocalDateTime asOf,
        Integer daysToExpiry,
        String dataQuality,
        String priceSource,
        Long priceLatencyMs
) {}
