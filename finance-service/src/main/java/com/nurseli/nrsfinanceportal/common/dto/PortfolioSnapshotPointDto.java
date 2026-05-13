package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioSnapshotPointDto(
        Long id,
        Instant snapshotAt,
        String triggerType,
        BigDecimal portfolioValueTry,
        BigDecimal portfolioCostTry,
        BigDecimal portfolioPnlTry
) {}
