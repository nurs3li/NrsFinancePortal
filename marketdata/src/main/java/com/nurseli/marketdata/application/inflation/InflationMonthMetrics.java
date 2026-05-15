package com.nurseli.marketdata.application.inflation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Aylık endeks serisi tek ay satırı: seviye + hesaplanan MoM / YoY yüzdeleri (yoksa null).
 */
public record InflationMonthMetrics(
        YearMonth yearMonth,
        BigDecimal indexValue,
        BigDecimal monthlyChangePercent,
        BigDecimal annualChangePercent
) {
    public LocalDate monthStart() {
        return yearMonth.atDay(1);
    }
}
