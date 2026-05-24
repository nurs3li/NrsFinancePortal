package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Aylık TÜFE endeks seviyesi; tarih için en yakın önceki resmi ay CPI çarpanını hesaplar.
 */
public final class CpiIndexLookup {

    private final NavigableMap<LocalDate, BigDecimal> indexByMonthStart;
    private final boolean available;

    public CpiIndexLookup(NavigableMap<LocalDate, BigDecimal> indexByMonthStart) {
        this.indexByMonthStart = indexByMonthStart != null ? indexByMonthStart : new TreeMap<>();
        this.available = !this.indexByMonthStart.isEmpty();
    }

    /**
     * Boş CPI lookup örneği döner.
     */
    public static CpiIndexLookup empty() {
        return new CpiIndexLookup(new TreeMap<>());
    }

    /**
     * Endeks verisi yüklü mü kontrol eder.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Tarih için en yakın önceki resmi ayı döner.
     */
    public Optional<LocalDate> monthAtOrBefore(LocalDate date) {
        if (!available || date == null) {
            return Optional.empty();
        }
        LocalDate key = YearMonth.from(date).atDay(1);
        var entry = indexByMonthStart.floorEntry(key);
        if (entry == null || entry.getValue() == null || entry.getValue().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(entry.getKey());
    }

    /**
     * Tarih için endeks değerini döner.
     */
    public Optional<BigDecimal> indexAtOrBefore(LocalDate date) {
        return monthAtOrBefore(date).flatMap(month -> {
            BigDecimal v = indexByMonthStart.get(month);
            if (v == null || v.signum() <= 0) {
                return Optional.empty();
            }
            return Optional.of(v);
        });
    }

    /**
     * Serideki en güncel resmi ayı döner.
     */
    public Optional<LocalDate> latestMonth() {
        if (!available) {
            return Optional.empty();
        }
        var last = indexByMonthStart.lastEntry();
        if (last == null || last.getValue() == null || last.getValue().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(last.getKey());
    }

    /**
     * Serideki en güncel endeks değerini döner.
     */
    public Optional<BigDecimal> latestIndex() {
        return latestMonth().map(indexByMonthStart::get)
                .filter(v -> v != null && v.signum() > 0);
    }
}
