package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

/**
 * Manuel portföy pozisyonlarından hesaplanan anlık değer / maliyet / PnL (TRY).
 */
public record PortfolioSnapshotMetricsDto(
        BigDecimal portfolioValueTry,
        BigDecimal portfolioCostTry,
        BigDecimal portfolioPnlTry
) {}
