package com.nurseli.marketdata.application.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapRangeResolverTest {

    private final BootstrapRangeResolver resolver = new BootstrapRangeResolver();

    @Test
    void resolveDaily_emptyDb_fullSeed() {
        ResolvedBootstrapRange r =
                resolver.resolveDaily(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 5, 22), Optional.empty());
        assertEquals(BootstrapMode.FULL_SEED, r.mode());
        assertEquals(LocalDate.of(2020, 1, 1), r.from());
        assertTrue(r.shouldFetch());
    }

    @Test
    void resolveDaily_upToDate_skips() {
        ResolvedBootstrapRange r =
                resolver.resolveDaily(
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2026, 5, 22),
                        Optional.of(LocalDate.of(2026, 5, 22)));
        assertEquals(BootstrapMode.SKIP, r.mode());
        assertFalse(r.shouldFetch());
    }

    @Test
    void resolveDaily_gapFromMaxPlusOne() {
        ResolvedBootstrapRange r =
                resolver.resolveDaily(
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2026, 5, 22),
                        Optional.of(LocalDate.of(2026, 5, 20)));
        assertEquals(BootstrapMode.GAP_FILL, r.mode());
        assertEquals(LocalDate.of(2026, 5, 21), r.from());
        assertTrue(r.shouldFetch());
    }

    @Test
    void resolveMonthly_gapFromNextMonth() {
        ResolvedBootstrapRange r =
                resolver.resolveMonthly(
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2026, 5, 22),
                        Optional.of(LocalDate.of(2026, 3, 1)));
        assertEquals(BootstrapMode.GAP_FILL, r.mode());
        assertEquals(LocalDate.of(2026, 4, 1), r.from());
        assertTrue(r.shouldFetch());
    }
}
