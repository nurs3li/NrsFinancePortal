package com.nurseli.marketdata.api.dto.loan;

import java.math.BigDecimal;

public record LoanRateLatestItemDto(
        String type,
        String label,
        String seriesCode,
        BigDecimal value,
        String description
) {}
