package com.nurseli.marketdata.application.bootstrap;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Boş DB → config {@code from}; dolu DB → {@code max(stored)+1} … {@code to}.
 */
@Component
public class BootstrapRangeResolver {

    public ResolvedBootstrapRange resolveDaily(LocalDate configFrom, LocalDate to, Optional<LocalDate> maxStored) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate seedFrom = configFrom != null ? configFrom : end;
        if (maxStored.isEmpty()) {
            return new ResolvedBootstrapRange(seedFrom, end, BootstrapMode.FULL_SEED, "empty_db");
        }
        LocalDate max = maxStored.get();
        if (!max.isBefore(end)) {
            return new ResolvedBootstrapRange(seedFrom, end, BootstrapMode.SKIP, "up_to_date max=" + max);
        }
        LocalDate gapFrom = max.plusDays(1);
        if (gapFrom.isBefore(seedFrom)) {
            gapFrom = seedFrom;
        }
        if (gapFrom.isAfter(end)) {
            return new ResolvedBootstrapRange(seedFrom, end, BootstrapMode.SKIP, "gap_empty");
        }
        return new ResolvedBootstrapRange(gapFrom, end, BootstrapMode.GAP_FILL, "gap_from max=" + max);
    }

    public ResolvedBootstrapRange resolveMonthly(LocalDate configFrom, LocalDate to, Optional<LocalDate> maxStored) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate seedFrom = configFrom != null ? configFrom : end.withDayOfMonth(1);
        if (maxStored.isEmpty()) {
            return new ResolvedBootstrapRange(seedFrom, end, BootstrapMode.FULL_SEED, "empty_db");
        }
        YearMonth maxYm = YearMonth.from(maxStored.get());
        YearMonth endYm = YearMonth.from(end);
        if (!maxYm.isBefore(endYm)) {
            return new ResolvedBootstrapRange(seedFrom, end, BootstrapMode.SKIP, "up_to_date maxMonth=" + maxYm);
        }
        LocalDate gapFrom = maxYm.plusMonths(1).atDay(1);
        if (gapFrom.isBefore(seedFrom)) {
            gapFrom = seedFrom;
        }
        if (gapFrom.isAfter(end)) {
            return new ResolvedBootstrapRange(seedFrom, end, BootstrapMode.SKIP, "gap_empty");
        }
        return new ResolvedBootstrapRange(gapFrom, end, BootstrapMode.GAP_FILL, "gap_from maxMonth=" + maxYm);
    }
}
