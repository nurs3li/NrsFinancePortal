package com.nurseli.marketdata.infrastructure.evds;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EvdsObservationDateParserTest {

    @Test
    void parsesDdMmYyyy() {
        assertEquals(LocalDate.of(2026, 5, 8), EvdsObservationDateParser.parse("08-05-2026"));
    }

    @Test
    void parsesYearMonth() {
        assertEquals(LocalDate.of(2026, 3, 1), EvdsObservationDateParser.parse("2026-03"));
    }

    @Test
    void parsesEvdsSingleDigitMonth() {
        assertEquals(LocalDate.of(2026, 4, 1), EvdsObservationDateParser.parse("2026-4"));
        assertEquals(LocalDate.of(2026, 1, 1), EvdsObservationDateParser.parse("2026-1"));
    }

    @Test
    void parsesIsoDate() {
        assertEquals(LocalDate.of(2026, 5, 8), EvdsObservationDateParser.parse("2026-05-08"));
    }

    @Test
    void nullOnGarbage() {
        assertNull(EvdsObservationDateParser.parse("n/a"));
    }
}
