package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioSnapshotPointDto(
        Long id,
        Instant snapshotAt,
        String triggerType,
        Long tradeId,
        BigDecimal combinedValueTry,
        BigDecimal combinedCostTry,
        BigDecimal combinedPnlTry,
        BigDecimal tradeValueTry,
        BigDecimal tradeCostTry,
        BigDecimal tradePnlTry,
        BigDecimal manualValueTry,
        BigDecimal manualCostTry,
        BigDecimal manualPnlTry
) {}
