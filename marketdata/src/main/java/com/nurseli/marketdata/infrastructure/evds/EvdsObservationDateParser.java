package com.nurseli.marketdata.infrastructure.evds;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * EVDS zaman serisi tarih alanları (Tarih / DATE) için ortak ayrıştırma.
 */
public final class EvdsObservationDateParser {

    private static final DateTimeFormatter EVDS_DAY_FIRST = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private EvdsObservationDateParser() {}

    public static LocalDate parse(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            // EVDS aylık seriler: "2026-4", "2026-10", "2023-12" (tek haneli ay yaygın)
            int dash = t.indexOf('-');
            if (dash == 4 && dash == t.lastIndexOf('-') && t.length() >= 6 && t.length() <= 7) {
                int year = Integer.parseInt(t.substring(0, 4));
                int month = Integer.parseInt(t.substring(5));
                if (month >= 1 && month <= 12) {
                    return YearMonth.of(year, month).atDay(1);
                }
            }
            if (t.length() >= 10 && t.charAt(4) == '-' && t.charAt(7) == '-') {
                return LocalDate.parse(t.substring(0, 10));
            }
            return LocalDate.parse(t, EVDS_DAY_FIRST);
        } catch (Exception ignored) {
            return null;
        }
    }
}
