package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Aylık TÜFE endeks seviyesi — tarih için en yakın önceki resmi ay.
 */
public final class CpiIndexLookup {

    private final NavigableMap<LocalDate, BigDecimal> indexByMonthStart;
    private final boolean available;

    public CpiIndexLookup(NavigableMap<LocalDate, BigDecimal> indexByMonthStart) {
        this.indexByMonthStart = indexByMonthStart != null ? indexByMonthStart : new TreeMap<>();
        this.available = !this.indexByMonthStart.isEmpty();
    }

    public static CpiIndexLookup empty() {
        return new CpiIndexLookup(new TreeMap<>());
    }

    public boolean isAvailable() {
        return available;
    }

    public Optional<BigDecimal> indexAtOrBefore(LocalDate date) {
        if (!available || date == null) {
            return Optional.empty();
        }
        LocalDate key = YearMonth.from(date).atDay(1);
        var entry = indexByMonthStart.floorEntry(key);
        if (entry == null || entry.getValue() == null || entry.getValue().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(entry.getValue());
    }

    public Optional<BigDecimal> latestIndex() {
        if (!available) {
            return Optional.empty();
        }
        var last = indexByMonthStart.lastEntry();
        if (last == null || last.getValue() == null || last.getValue().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(last.getValue());
    }
}
