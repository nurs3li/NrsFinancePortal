package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.CpiTrMacroResponse;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpiTrComputationTest {

    @Test
    void fromAscendingPoints_matchesPublishedExample() {
        List<EvdsSeriesPoint> points = List.of(
                p(LocalDate.of(2025, 4, 1), "3043.23"),
                p(LocalDate.of(2026, 3, 1), "3866.74"),
                p(LocalDate.of(2026, 4, 1), "4028.47")
        );
        Optional<CpiTrMacroResponse> out = CpiTrComputation.fromAscendingPoints(points, "TP_GENENDEKS_T1");
        assertTrue(out.isPresent());
        CpiTrMacroResponse r = out.get();
        assertEquals("TP_GENENDEKS_T1", r.seriesCode());
        assertEquals(LocalDate.of(2026, 4, 1), r.indexMonth());
        assertEquals(0, new BigDecimal("4028.47").compareTo(r.cpiTrIndex()));
        assertEquals(new BigDecimal("4.1826"), r.cpiTrMonthly(), "MoM (BigDecimal divide scale 10, sonra %)");
        assertEquals(new BigDecimal("32.3748"), r.cpiTrAnnual(), "YoY");
        assertEquals(null, r.annualNote());
    }

    private static EvdsSeriesPoint p(LocalDate day, String value) {
        return new EvdsSeriesPoint(day.atStartOfDay(), new BigDecimal(value));
    }
}
