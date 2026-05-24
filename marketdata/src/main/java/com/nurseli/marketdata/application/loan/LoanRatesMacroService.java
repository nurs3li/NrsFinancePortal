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
import com.nurseli.marketdata.infrastructure.persistence.LoanRateWeeklyObservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanRatesMacroService {

    private static final int LATEST_STALE_DAYS = 14;

    private final EvdsProperties evdsProperties;
    private final EvdsDebtClient evdsDebtClient;
    private final LoanRatesPersistenceService loanRatesPersistenceService;
    private final LoanRateWeeklyObservationRepository loanRateWeeklyObservationRepository;

    public LoanRatesLatestResponse latest() {
        List<LoanRateLatestItemDto> items = new ArrayList<>();
        LocalDate maxAsOf = null;
        List<LoanRatesPersistenceService.LoanRateObservationRow> persistBatch = new ArrayList<>();
        if (!evdsProperties.isEnabled()) {
            return new LoanRatesLatestResponse("EVDS", "WEEKLY", "PERCENT", null, items);
        }
        LocalDate today = LocalDate.now();
        for (LoanRateCatalog.LoanRateSeriesSpec spec : LoanRateCatalog.allSpecs()) {
            String code = resolveSeriesCode(spec.evdsLogicalSeriesKey());
            if (code == null) {
                continue;
            }
            Optional<LoanRateWeeklyObservation> dbRow =
                    loanRateWeeklyObservationRepository.findFirstBySubTypeOrderByObservedDateDesc(spec.subType());
            if (dbRow.isPresent() && isFresh(dbRow.get().getObservedDate(), today)) {
                LoanRateWeeklyObservation row = dbRow.get();
                items.add(new LoanRateLatestItemDto(
                        spec.subType().name(),
                        spec.label(),
                        code,
                        row.getRatePercent(),
                        spec.description()));
                maxAsOf = maxDate(maxAsOf, row.getObservedDate());
                continue;
            }
            Optional<EvdsSeriesPoint> last = fetchLastValidPoint(code);
            if (last.isEmpty()) {
                continue;
            }
            EvdsSeriesPoint p = last.get();
            LocalDate d = p.asOf().toLocalDate();
            BigDecimal v = p.value();
            persistBatch.add(new LoanRatesPersistenceService.LoanRateObservationRow(code, spec.subType(), d, v));
            items.add(new LoanRateLatestItemDto(
                    spec.subType().name(),
                    spec.label(),
                    code,
                    v,
                    spec.description()
            ));
            maxAsOf = maxDate(maxAsOf, d);
        }
        loanRatesPersistenceService.upsertAll(persistBatch);
        return new LoanRatesLatestResponse("EVDS", "WEEKLY", "PERCENT", maxAsOf, items);
    }

    public LoanRatesHistoryResponse history(Set<LoanRateSubtype> types, LocalDate fromInclusive, LocalDate toInclusive) {
        List<LoanRateHistorySeriesDto> series = new ArrayList<>();
        if (!evdsProperties.isEnabled() || fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            return new LoanRatesHistoryResponse("EVDS", "WEEKLY", "PERCENT", series);
        }
        for (LoanRateSubtype sub : types) {
            LoanRateCatalog.LoanRateSeriesSpec spec = LoanRateCatalog.specFor(sub);
            String code = resolveSeriesCode(spec.evdsLogicalSeriesKey());
            if (code == null) {
                series.add(new LoanRateHistorySeriesDto(sub.name(), spec.label(), "", List.of()));
                continue;
            }
            HistoryLoad loaded = loadHistoryPoints(code, sub, spec, fromInclusive, toInclusive);
            series.add(new LoanRateHistorySeriesDto(sub.name(), spec.label(), code, loaded.points()));
            log.info(
                    "[LOAN_RATES] history subType={} mode={} points={} from={} to={}",
                    sub,
                    loaded.mode(),
                    loaded.points().size(),
                    fromInclusive,
                    toInclusive);
        }
        return new LoanRatesHistoryResponse("EVDS", "WEEKLY", "PERCENT", series);
    }

    /**
     * DB’den aralık okuma (EVDS çağrısı yapmaz).
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
            List<LoanRateHistoryPointDto> points = mapDbRows(
                    loanRateWeeklyObservationRepository.findBySubTypeAndObservedDateBetweenOrderByObservedDateAsc(
                            sub, fromInclusive, toInclusive));
            series.add(new LoanRateHistorySeriesDto(sub.name(), spec.label(), code, points));
        }
        return new LoanRatesHistoryResponse("EVDS", "WEEKLY", "PERCENT", series);
    }

    private record HistoryLoad(String mode, List<LoanRateHistoryPointDto> points) {}

    private HistoryLoad loadHistoryPoints(
            String code,
            LoanRateSubtype sub,
            LoanRateCatalog.LoanRateSeriesSpec spec,
            LocalDate fromInclusive,
            LocalDate toInclusive
    ) {
        try {
            List<LoanRateWeeklyObservation> dbRows =
                    loanRateWeeklyObservationRepository.findBySubTypeAndObservedDateBetweenOrderByObservedDateAsc(
                            sub, fromInclusive, toInclusive);
            List<LoanRateHistoryPointDto> dbPoints = mapDbRows(dbRows);
            if (!dbPoints.isEmpty()) {
                LocalDate dbLast = parseDate(dbPoints.getLast().date());
                if (dbLast != null && !dbLast.isBefore(toInclusive)) {
                    return new HistoryLoad("db_hit", dbPoints);
                }
                if (dbLast != null) {
                    LocalDate gapFrom = dbLast.plusDays(1);
                    if (!gapFrom.isAfter(toInclusive)) {
                        List<LoanRateHistoryPointDto> merged =
                                mergeHistory(dbPoints, fetchEvdsPoints(code, gapFrom, toInclusive));
                        return new HistoryLoad("db_gap", merged);
                    }
                }
                return new HistoryLoad("db_hit", dbPoints);
            }
            LocalDate fetchStart = fromInclusive.minusWeeks(2);
            List<LoanRateHistoryPointDto> full = fetchEvdsPoints(code, fetchStart, toInclusive).stream()
                    .filter(p -> {
                        LocalDate d = parseDate(p.date());
                        return d != null && !d.isBefore(fromInclusive) && !d.isAfter(toInclusive);
                    })
                    .sorted(Comparator.comparing(LoanRateHistoryPointDto::date))
                    .toList();
            return new HistoryLoad("evds_full", full);
        } catch (Exception ex) {
            log.warn("[LOAN_RATES] history read failed subType={} msg={}", sub, ex.getMessage());
            List<LoanRateHistoryPointDto> dbOnly = mapDbRows(
                    loanRateWeeklyObservationRepository.findBySubTypeAndObservedDateBetweenOrderByObservedDateAsc(
                            sub, fromInclusive, toInclusive));
            return new HistoryLoad("db_fallback", dbOnly);
        }
    }

    private List<LoanRateHistoryPointDto> fetchEvdsPoints(String code, LocalDate fetchStart, LocalDate fetchEnd) {
        try {
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
                points.add(new LoanRateHistoryPointDto(d.toString(), v));
            }
            points.sort(Comparator.comparing(LoanRateHistoryPointDto::date));
            return points;
        } catch (Exception ex) {
            log.warn("[LOAN_RATES] evds fetch failed series={} {}..{} msg={}", code, fetchStart, fetchEnd, ex.getMessage());
            return List.of();
        }
    }

    private static List<LoanRateHistoryPointDto> mapDbRows(List<LoanRateWeeklyObservation> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(r -> r.getObservedDate() != null && r.getRatePercent() != null)
                .map(r -> new LoanRateHistoryPointDto(r.getObservedDate().toString(), r.getRatePercent()))
                .toList();
    }

    private static List<LoanRateHistoryPointDto> mergeHistory(
            List<LoanRateHistoryPointDto> base,
            List<LoanRateHistoryPointDto> extra
    ) {
        Map<String, LoanRateHistoryPointDto> map = new LinkedHashMap<>();
        for (LoanRateHistoryPointDto p : base) {
            if (p != null && p.date() != null) {
                map.put(p.date(), p);
            }
        }
        for (LoanRateHistoryPointDto p : extra) {
            if (p != null && p.date() != null) {
                map.put(p.date(), p);
            }
        }
        return map.values().stream().sorted(Comparator.comparing(LoanRateHistoryPointDto::date)).toList();
    }

    private static boolean isFresh(LocalDate observed, LocalDate today) {
        if (observed == null) {
            return false;
        }
        return !observed.isBefore(today.minusDays(LATEST_STALE_DAYS));
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

    private static LocalDate parseDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(iso.trim());
        } catch (Exception ex) {
            return null;
        }
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
