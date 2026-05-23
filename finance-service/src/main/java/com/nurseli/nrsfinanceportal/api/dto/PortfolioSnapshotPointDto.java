package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Portföy snapshot noktası DTO'su; anlık değer, maliyet ve PnL ile tetikleyici bilgisini taşır.
 */
public record PortfolioSnapshotPointDto(
        Long id,
        Instant snapshotAt,
        String triggerType,
        BigDecimal portfolioValueTry,
        BigDecimal portfolioCostTry,
        BigDecimal portfolioPnlTry
) {}
