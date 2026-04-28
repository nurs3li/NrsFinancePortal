package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ViopSnapshotResponse(
        String contractCode,
        BigDecimal price,
        BigDecimal theoreticalSpot,
        BigDecimal basis,
        BigDecimal annualizedBasisPct,
        Long openInterest,
        String oiPriceRegime,
        String source,
        LocalDateTime asOf
) {}
