package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;

public record PreciousMetalUsdChanges(
        BigDecimal changeDailyPercent,
        BigDecimal changeWeeklyPercent,
        BigDecimal changeMonthlyPercent,
        BigDecimal changeYearlyPercent
) {}
