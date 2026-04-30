package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DebtSnapshotResponse(
        String isin,
        BigDecimal dirtyPrice,
        BigDecimal yieldPct,
        String maturityDate,
        Long daysToMaturity,
        BigDecimal couponRate,
        String source,
        LocalDateTime asOf,
        String quality,
        Boolean synthetic
) {}
