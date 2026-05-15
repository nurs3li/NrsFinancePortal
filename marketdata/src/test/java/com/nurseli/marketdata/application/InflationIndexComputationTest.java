package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.inflation.InflationIndexComputation;
import com.nurseli.marketdata.application.inflation.InflationMonthMetrics;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InflationIndexComputationTest {

    @Test
    void buildSortedMonthlySeries_ppiIndex_momAndYoy() {
        List<EvdsSeriesPoint> points = List.of(
                p(LocalDate.of(2024, 1, 1), "100"),
                p(LocalDate.of(2024, 2, 1), "102"),
                p(LocalDate.of(2025, 1, 1), "110"),
                p(LocalDate.of(2025, 2, 1), "115.5")
        );
        List<InflationMonthMetrics> s = InflationIndexComputation.buildSortedMonthlySeries(points);
        InflationMonthMetrics feb2025 = s.stream().filter(x -> x.yearMonth().equals(YearMonth.of(2025, 2))).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("115.5").compareTo(feb2025.indexValue()));
        // MoM: 115.5/110 - 1 = 5%
        assertEquals(new BigDecimal("5.0000"), feb2025.monthlyChangePercent());
        // YoY: 115.5/102 - 1
        assertEquals(new BigDecimal("13.2353"), feb2025.annualChangePercent());
    }

    @Test
    void monthlyChangePct_nullWhenPreviousMonthMissing() {
        List<EvdsSeriesPoint> points = List.of(
                p(LocalDate.of(2025, 1, 1), "100"),
                p(LocalDate.of(2025, 3, 1), "105")
        );
        List<InflationMonthMetrics> s = InflationIndexComputation.buildSortedMonthlySeries(points);
        InflationMonthMetrics mar = s.stream().filter(x -> x.yearMonth().equals(YearMonth.of(2025, 3))).findFirst().orElseThrow();
        assertNull(mar.monthlyChangePercent());
        assertNull(mar.annualChangePercent());
    }

    @Test
    void annualChangePct_nullWhenSameMonthLastYearMissing() {
        List<EvdsSeriesPoint> points = List.of(
                p(LocalDate.of(2025, 1, 1), "100"),
                p(LocalDate.of(2025, 2, 1), "102")
        );
        List<InflationMonthMetrics> s = InflationIndexComputation.buildSortedMonthlySeries(points);
        InflationMonthMetrics feb = s.stream().filter(x -> x.yearMonth().equals(YearMonth.of(2025, 2))).findFirst().orElseThrow();
        assertEquals(new BigDecimal("2.0000"), feb.monthlyChangePercent());
        assertNull(feb.annualChangePercent());
    }

    @Test
    void pctChange_matchesFormula() {
        BigDecimal mom = InflationIndexComputation.pctChange(new BigDecimal("110"), new BigDecimal("100"));
        assertEquals(new BigDecimal("10.0000"), mom);
    }

    private static EvdsSeriesPoint p(LocalDate day, String value) {
        return new EvdsSeriesPoint(day.atStartOfDay(), new BigDecimal(value));
    }
}
