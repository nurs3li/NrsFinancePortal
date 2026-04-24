package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

/**
 * Bir kullanıcı için anlık portföy metrikleri (birleşik / trade / manuel).
 */
public record PortfolioSnapshotMetricsDto(
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
