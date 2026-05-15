package com.nurseli.marketdata.application.loan;

import com.nurseli.marketdata.api.dto.loan.LoanRateHistoryPointDto;
import com.nurseli.marketdata.api.dto.loan.LoanRateHistorySeriesDto;
import com.nurseli.marketdata.api.dto.loan.LoanRateLatestItemDto;
import com.nurseli.marketdata.api.dto.loan.LoanRatesHistoryResponse;
import com.nurseli.marketdata.api.dto.loan.LoanRatesLatestResponse;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.domain.loan.LoanRateSubtype;
import com.nurseli.marketdata.domain.loan.LoanRateWeeklyObservation;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import com.nurseli.marketdata.repository.LoanRateWeeklyObservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanRatesMacroService {

    private final EvdsProperties evdsProperties;
    private final EvdsDebtClient evdsDebtClient;
    private final LoanRatesPersistenceService loanRatesPersistenceService;
    private final LoanRateWeeklyObservationRepository loanRateWeeklyObservationRepository;

    public LoanRatesLatestResponse latest() {
        List<LoanRateLatestItemDto> items = new ArrayList<>();
        LocalDate maxAsOf = null;
        if (!evdsProperties.isEnabled()) {
            return new LoanRatesLatestResponse("EVDS", "WEEKLY", "PERCENT", null, items);
        }
        for (LoanRateCatalog.LoanRateSeriesSpec spec : LoanRateCatalog.allSpecs()) {
            String code = resolveSeriesCode(spec.evdsLogicalSeriesKey());
            if (code == null) {
                continue;
            }
            Optional<EvdsSeriesPoint> last = fetchLastValidPoint(code);
            if (last.isEmpty()) {
                continue;
            }
            EvdsSeriesPoint p = last.get();
            LocalDate d = p.asOf().toLocalDate();
            BigDecimal v = p.value();
            loanRatesPersistenceService.upsert(code, spec.subType(), d, v);
            items.add(new LoanRateLatestItemDto(
                    spec.subType().name(),
                    spec.label(),
                    code,
                    v,
                    spec.description()
            ));
            maxAsOf = maxDate(maxAsOf, d);
        }
        return new LoanRatesLatestResponse("EVDS", "WEEKLY", "PERCENT", maxAsOf, items);
    }

    public LoanRatesHistoryResponse history(Set<LoanRateSubtype> types, LocalDate fromInclusive, LocalDate toInclusive) {
        List<LoanRateHistorySeriesDto> series = new ArrayList<>();
        if (!evdsProperties.isEnabled() || fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            return new LoanRatesHistoryResponse("EVDS", "WEEKLY", "PERCENT", series);
        }
        LocalDate fetchStart = fromInclusive.minusWeeks(2);
        LocalDate fetchEnd = toInclusive;
        for (LoanRateSubtype sub : types) {
            LoanRateCatalog.LoanRateSeriesSpec spec = LoanRateCatalog.specFor(sub);
            String code = resolveSeriesCode(spec.evdsLogicalSeriesKey());
            if (code == null) {
                series.add(new LoanRateHistorySeriesDto(sub.name(), spec.label(), "", List.of()));
                continue;
            }
            List<EvdsSeriesPoint> pts = evdsDebtClient.fetchSeriesAscending(code, fetchStart, fetchEnd);
            List<LoanRateHistoryPointDto> points = new ArrayList<>();
            for (EvdsSeriesPoint p : pts) {
                if (p == null || p.asOf() == null) {
                    continue;
                }
                BigDecimal v = p.value();
                if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                LocalDate d = p.asOf().toLocalDate();
                if (d.isBefore(fromInclusive) || d.isAfter(toInclusive)) {
                    continue;
                }
                loanRatesPersistenceService.upsert(code, sub, d, v);
                points.add(new LoanRateHistoryPointDto(d.toString(), v));
            }
            points.sort(Comparator.comparing(LoanRateHistoryPointDto::date));
            series.add(new LoanRateHistorySeriesDto(sub.name(), spec.label(), code, points));
        }
        return new LoanRatesHistoryResponse("EVDS", "WEEKLY", "PERCENT", series);
    }

    /**
     * DB’den aralık okuma (EVDS çağrısı yapmaz) — test ve raporlama için.
     */
    public LoanRatesHistoryResponse historyFromDb(Set<LoanRateSubtype> types, LocalDate fromInclusive, LocalDate toInclusive) {
        List<LoanRateHistorySeriesDto> series = new ArrayList<>();
        for (LoanRateSubtype sub : types) {
            LoanRateCatalog.LoanRateSeriesSpec spec = LoanRateCatalog.specFor(sub);
            String code = resolveSeriesCode(spec.evdsLogicalSeriesKey());
            if (code == null) {
                series.add(new LoanRateHistorySeriesDto(sub.name(), spec.label(), "", List.of()));
                continue;
            }
            List<LoanRateWeeklyObservation> rows = loanRateWeeklyObservationRepository
                    .findBySubTypeAndObservedDateBetweenOrderByObservedDateAsc(sub, fromInclusive, toInclusive);
            List<LoanRateHistoryPointDto> points = rows.stream()
                    .map(r -> new LoanRateHistoryPointDto(r.getObservedDate().toString(), r.getRatePercent()))
                    .toList();
            series.add(new LoanRateHistorySeriesDto(sub.name(), spec.label(), code, points));
        }
        return new LoanRatesHistoryResponse("EVDS", "WEEKLY", "PERCENT", series);
    }

    private String resolveSeriesCode(String logicalKey) {
        String raw = evdsProperties.getSeriesCode(logicalKey);
        if (raw == null || raw.isBlank()) {
            log.warn("[LOAN_RATES] missing EVDS series mapping for logicalKey={}", logicalKey);
            return null;
        }
        return raw.trim();
    }

    private Optional<EvdsSeriesPoint> fetchLastValidPoint(String seriesCode) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(110);
        List<EvdsSeriesPoint> pts = evdsDebtClient.fetchSeriesAscending(seriesCode, start, end);
        EvdsSeriesPoint last = null;
        for (EvdsSeriesPoint p : pts) {
            if (p == null || p.asOf() == null) {
                continue;
            }
            BigDecimal v = p.value();
            if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            last = p;
        }
        return Optional.ofNullable(last);
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? b : a;
    }
}
