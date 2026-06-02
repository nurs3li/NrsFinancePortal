package com.nurseli.marketdata.application.macropanel;

import com.nurseli.marketdata.api.dto.deposit.DepositRateHistoryRowDto;
import com.nurseli.marketdata.api.dto.inflation.InflationIndicatorSnapshotDto;
import com.nurseli.marketdata.api.dto.inflation.InflationLatestResponse;
import com.nurseli.marketdata.api.dto.loan.LoanRateHistoryPointDto;
import com.nurseli.marketdata.api.dto.loan.LoanRateHistorySeriesDto;
import com.nurseli.marketdata.api.dto.loan.LoanRatesHistoryResponse;
import com.nurseli.marketdata.api.dto.macropanel.InterestInflationMacroPanelResponse;
import com.nurseli.marketdata.api.dto.macropanel.MacroPanelBondSummaryDto;
import com.nurseli.marketdata.api.dto.macropanel.MacroPanelDerivedMetricsDto;
import com.nurseli.marketdata.api.dto.macropanel.NormalizedMacroObservationDto;
import com.nurseli.marketdata.api.dto.macropanel.NormalizedMacroSeriesDto;
import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.application.deposit.DepositRateCatalog;
import com.nurseli.marketdata.application.deposit.DepositRatesQueryService;
import com.nurseli.marketdata.application.inflation.InflationIndexQueryService;
import com.nurseli.marketdata.application.inflation.InflationMonthMetrics;
import com.nurseli.marketdata.application.loan.LoanRateCatalog;
import com.nurseli.marketdata.application.loan.LoanRatesMacroService;
import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.config.InflationCpiProperties;
import com.nurseli.marketdata.config.InflationPpiProperties;
import com.nurseli.marketdata.application.query.DebtQueryService;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.domain.loan.LoanRateSubtype;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Faiz &amp; Enflasyon paneli — önce DB (bootstrap), eksik kuyruk için tek EVDS gap çağrısı.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MacroPanelAggregationService {

    public static final String CATEGORY_POLICY_RATE = "POLICY_RATE";
    public static final String CATEGORY_FUNDING_COST = "FUNDING_COST";
    public static final String CATEGORY_CPI_INDEX = "CPI_INDEX";
    public static final String CATEGORY_PPI_INDEX = "PPI_INDEX";
    public static final String CATEGORY_CREDIT_RATE = "CREDIT_RATE";
    public static final String CATEGORY_DEPOSIT_RATE = "DEPOSIT_RATE";
    public static final String CATEGORY_FX_DEPOSIT_RATE = "FX_DEPOSIT_RATE";
    public static final String CATEGORY_BOND_PRICE_PERFORMANCE = "BOND_PRICE_PERFORMANCE";

    private static final String SOURCE_EVDS = "EVDS";
    private static final String FREQ_MONTHLY = "MONTHLY";
    private static final String FREQ_WEEKLY = "WEEKLY";
    private static final String UNIT_PERCENT = "PERCENT";
    private static final String UNIT_INDEX = "INDEX";
    private static final String UNIT_PRICE = "PRICE";
    private static final String DATA_TYPE_FLOW = "FLOW";

    private final EvdsProperties evdsProperties;
    private final EvdsDebtClient evdsDebtClient;
    private final InflationIndexQueryService inflationIndexQueryService;
    private final DepositRatesProperties depositRatesProperties;
    private final DepositRatesQueryService depositRatesQueryService;
    private final LoanRatesMacroService loanRatesMacroService;
    private final InflationCpiProperties inflationCpiProperties;
    private final InflationPpiProperties inflationPpiProperties;
    private final DebtQueryService debtQueryService;

    @Cacheable(cacheNames = "market:macro:interest-inflation-panel", key = "'snapshot'")
    public InterestInflationMacroPanelResponse build() {
        String generatedAt = Instant.now().toString();
        List<String> notes = new ArrayList<>();
        notes.add(
                "Kredi faizleri borçlanma maliyetidir (EVDS akım, haftalık). Mevduat faizleri tasarruf sahibi için yaklaşık akım getiri göstergesidir.");
        notes.add(
                "TÜFE/Yİ-ÜFE serileri endeks seviyesidir; MoM/YoY türetilmiş yüzdeler derived bölümünde özetlenir.");
        notes.add(
                "Tahvil satırlarında dirty price fiyat performansıdır; listedOranPercentNotYtm YTM değildir; yapılandırılmış YTM verisi yoksa hasStructuredYieldData=false.");
        notes.add(
                "Seriler önce PostgreSQL (bootstrap) okunur. HTTP panel yolu canlı EVDS gap yapmaz; güncelleme bootstrap ve scheduler ile yapılır.");
        notes.add(
                "Politika faizi ve fonlama maliyeti için DB tablosu yoksa yalnızca bu iki seri panelde canlı EVDS ile okunur.");

        if (!evdsProperties.isEnabled()) {
            log.info("[MACRO_PANEL] evds disabled — empty series");
            return new InterestInflationMacroPanelResponse(
                    generatedAt,
                    List.of(),
                    MacroPanelDerivedMetricsDto.empty(),
                    bondSummaries(),
                    notes
            );
        }

        List<NormalizedMacroSeriesDto> series = new ArrayList<>();
        appendMonthlyIndexSeries(series, EvdsSeriesLogicalNames.CPI_TR_INDEX, CATEGORY_CPI_INDEX, "TÜFE genel endeks (seviye)", 48);
        appendMonthlyIndexSeries(series, EvdsSeriesLogicalNames.PPI_TR_INDEX, CATEGORY_PPI_INDEX, "Yİ-ÜFE genel endeks (seviye)", 48);
        appendPolicySeries(series);
        appendFundingCostSeries(series);
        appendCreditSeries(series);
        appendTryDepositSeries(series);
        appendFxDepositSeries(series);

        MacroPanelDerivedMetricsDto derived = computeDerived(series);
        return new InterestInflationMacroPanelResponse(generatedAt, series, derived, bondSummaries(), notes);
    }

    private void appendMonthlyIndexSeries(
            List<NormalizedMacroSeriesDto> out,
            String logicalKey,
            String category,
            String label,
            int lookbackMonths
    ) {
        String code = evdsProperties.getSeriesCode(logicalKey);
        if (code == null || code.isBlank()) {
            log.warn("[MACRO_PANEL] missing evds mapping logicalKey={}", logicalKey);
            return;
        }
        String trimmed = code.trim();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(lookbackMonths).withDayOfMonth(1);
        List<NormalizedMacroObservationDto> obs = loadIndexObservations(logicalKey, trimmed, start, end);
        logSeries(trimmed, logicalKey, obs, obs.isEmpty() ? "miss" : "ok");
        if (!obs.isEmpty()) {
            out.add(new NormalizedMacroSeriesDto(trimmed, label, category, FREQ_MONTHLY, UNIT_INDEX, SOURCE_EVDS, obs, logicalKey));
        }
    }

    private void appendPolicySeries(List<NormalizedMacroSeriesDto> out) {
        String code = evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.POLICY_RATE_TR);
        if (code == null || code.isBlank()) {
            log.warn("[MACRO_PANEL] missing evds mapping logicalKey={}", EvdsSeriesLogicalNames.POLICY_RATE_TR);
            return;
        }
        String trimmed = code.trim();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(84).withDayOfMonth(1);
        List<NormalizedMacroObservationDto> obs = loadEvdsOnly(trimmed, EvdsSeriesLogicalNames.POLICY_RATE_TR, start, end);
        logSeries(trimmed, EvdsSeriesLogicalNames.POLICY_RATE_TR, obs, "evds");
        if (!obs.isEmpty()) {
            out.add(new NormalizedMacroSeriesDto(
                    trimmed,
                    "TCMB politika faiz oranı (TR)",
                    CATEGORY_POLICY_RATE,
                    FREQ_MONTHLY,
                    UNIT_PERCENT,
                    SOURCE_EVDS,
                    obs,
                    EvdsSeriesLogicalNames.POLICY_RATE_TR
            ));
        }
    }

    private void appendFundingCostSeries(List<NormalizedMacroSeriesDto> out) {
        String logicalKey = EvdsSeriesLogicalNames.TCMB_WEIGHTED_AVG_FUNDING_COST_TR;
        String code = evdsProperties.getSeriesCode(logicalKey);
        if (code == null || code.isBlank()) {
            log.warn("[MACRO_PANEL] missing evds mapping logicalKey={}", logicalKey);
            return;
        }
        String trimmed = code.trim();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(120);
        List<NormalizedMacroObservationDto> obs = loadEvdsOnly(trimmed, logicalKey, start, end);
        logSeries(trimmed, logicalKey, obs, "evds");
        if (!obs.isEmpty()) {
            out.add(new NormalizedMacroSeriesDto(
                    trimmed,
                    "TCMB Ağırlıklı Ortalama Fonlama Maliyeti",
                    CATEGORY_FUNDING_COST,
                    FREQ_WEEKLY,
                    UNIT_PERCENT,
                    SOURCE_EVDS,
                    obs,
                    logicalKey
            ));
        }
    }

    private void appendCreditSeries(List<NormalizedMacroSeriesDto> out) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(120);
        LoanRatesHistoryResponse dbBatch =
                loanRatesMacroService.historyFromDb(EnumSet.allOf(LoanRateSubtype.class), start, end);
        Map<String, List<LoanRateHistoryPointDto>> bySubtype = indexLoanHistory(dbBatch);

        for (LoanRateCatalog.LoanRateSeriesSpec spec : LoanRateCatalog.allSpecs()) {
            String code = evdsProperties.getSeriesCode(spec.evdsLogicalSeriesKey());
            if (code == null || code.isBlank()) {
                log.warn("[MACRO_PANEL] missing evds mapping logicalKey={}", spec.evdsLogicalSeriesKey());
                continue;
            }
            String trimmed = code.trim();
            List<NormalizedMacroObservationDto> dbObs = toObservationsFromLoanPoints(
                    bySubtype.getOrDefault(spec.subType().name(), List.of()));
            List<NormalizedMacroObservationDto> obs = dbObs;
            logSeries(trimmed, spec.evdsLogicalSeriesKey(), obs, dbObs.isEmpty() ? "evds_full" : "db+gap");
            if (!obs.isEmpty()) {
                out.add(new NormalizedMacroSeriesDto(
                        trimmed,
                        spec.label() + " (borçlanma maliyeti, akım %)",
                        CATEGORY_CREDIT_RATE,
                        FREQ_WEEKLY,
                        UNIT_PERCENT,
                        SOURCE_EVDS,
                        obs,
                        spec.evdsLogicalSeriesKey()
                ));
            }
        }
    }

    private void appendTryDepositSeries(List<NormalizedMacroSeriesDto> out) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(120);
        for (DepositRateCatalog.DepositRateSeriesSpec spec : DepositRateCatalog.allSpecs()) {
            if (!"TRY".equalsIgnoreCase(spec.currency())) {
                continue;
            }
            appendDepositSeries(out, spec, start, end, CATEGORY_DEPOSIT_RATE, tryDepositLabel(spec.term()), null, null);
        }
    }

    private void appendFxDepositSeries(List<NormalizedMacroSeriesDto> out) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(120);
        for (DepositRateCatalog.DepositRateSeriesSpec spec : DepositRateCatalog.allSpecs()) {
            if ("TRY".equalsIgnoreCase(spec.currency())) {
                continue;
            }
            String logicalKey = spec.evdsLogicalKey();
            appendDepositSeries(
                    out,
                    spec,
                    start,
                    end,
                    CATEGORY_FX_DEPOSIT_RATE,
                    fxDepositLabel(spec.currency(), spec.term()),
                    spec.currency().toUpperCase(),
                    spec.term().toUpperCase()
            );
        }
    }

    private void appendDepositSeries(
            List<NormalizedMacroSeriesDto> out,
            DepositRateCatalog.DepositRateSeriesSpec spec,
            LocalDate start,
            LocalDate end,
            String category,
            String label,
            String currency,
            String tenor
    ) {
        String code = evdsProperties.getSeriesCode(spec.evdsLogicalKey());
        if (code == null || code.isBlank()) {
            log.warn("[MACRO_PANEL] missing evds mapping logicalKey={}", spec.evdsLogicalKey());
            return;
        }
        String trimmed = code.trim();
        List<NormalizedMacroObservationDto> dbObs = loadDepositObservationsFromDb(spec.currency(), spec.term(), start, end);
        // HTTP okuma yolu: yalnızca DB (bootstrap doldurur). Canlı EVDS gap burada 10+ seri × throttle → 60–120 sn gecikme yaratıyordu.
        List<NormalizedMacroObservationDto> obs = dbObs;
        logSeries(trimmed, spec.evdsLogicalKey(), obs, dbObs.isEmpty() ? "evds_full" : "db+gap");
        boolean includeEmptyFx = CATEGORY_FX_DEPOSIT_RATE.equals(category);
        if (!obs.isEmpty() || includeEmptyFx) {
            out.add(new NormalizedMacroSeriesDto(
                    trimmed,
                    label,
                    category,
                    FREQ_WEEKLY,
                    UNIT_PERCENT,
                    SOURCE_EVDS,
                    obs,
                    spec.evdsLogicalKey(),
                    currency,
                    tenor,
                    DATA_TYPE_FLOW
            ));
        }
    }

    private static String tryDepositLabel(String term) {
        return switch (String.valueOf(term).toUpperCase()) {
            case "1M" -> "1 aya kadar vadeli TL mevduat faizi (akım, %)";
            case "3M" -> "3 aya kadar vadeli TL mevduat faizi (akım, %)";
            case "6M" -> "6 aya kadar vadeli TL mevduat faizi (akım, %)";
            case "1Y" -> "1 yıla kadar vadeli TL mevduat faizi (akım, %)";
            case "GT1Y" -> "1 yıl ve daha uzun vadeli TL mevduat faizi (akım, %)";
            default -> "TL mevduat faizi (" + term + ", akım %)";
        };
    }

    private static String fxDepositLabel(String currency, String term) {
        String ccy = String.valueOf(currency).toUpperCase();
        return switch (String.valueOf(term).toUpperCase()) {
            case "1M" -> "1 aya kadar vadeli " + ccy + " mevduat faizi (akım, %, EVDS)";
            case "3M" -> "3 aya kadar vadeli " + ccy + " mevduat faizi (akım, %, EVDS)";
            case "6M" -> "6 aya kadar vadeli " + ccy + " mevduat faizi (akım, %, EVDS)";
            case "1Y" -> "1 yıla kadar vadeli " + ccy + " mevduat faizi (akım, %, EVDS)";
            default -> ccy + " mevduat faizi (" + term + ", akım %, EVDS)";
        };
    }

    private List<NormalizedMacroObservationDto> loadDepositObservationsFromDb(
            String currency,
            String term,
            LocalDate start,
            LocalDate end
    ) {
        if (!depositRatesProperties.isEnabled()) {
            return List.of();
        }
        return depositRatesQueryService.history(currency, term, start, end).stream()
                .filter(row -> row != null && row.observationDate() != null && row.ratePercent() != null)
                .filter(row -> row.ratePercent().compareTo(BigDecimal.ZERO) > 0)
                .map(row -> new NormalizedMacroObservationDto(
                        row.observationDate().toString(),
                        row.ratePercent().doubleValue()))
                .sorted(Comparator.comparing(NormalizedMacroObservationDto::date))
                .toList();
    }

    private static Map<String, List<LoanRateHistoryPointDto>> indexLoanHistory(LoanRatesHistoryResponse batch) {
        Map<String, List<LoanRateHistoryPointDto>> map = new LinkedHashMap<>();
        if (batch == null || batch.series() == null) {
            return map;
        }
        for (LoanRateHistorySeriesDto s : batch.series()) {
            if (s != null && s.type() != null) {
                map.put(s.type(), s.points() != null ? s.points() : List.of());
            }
        }
        return map;
    }

    private static List<NormalizedMacroObservationDto> toObservationsFromLoanPoints(List<LoanRateHistoryPointDto> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<NormalizedMacroObservationDto> out = new ArrayList<>();
        for (LoanRateHistoryPointDto p : points) {
            if (p == null || p.date() == null || p.value() == null) {
                continue;
            }
            BigDecimal v = p.value();
            if (v.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            out.add(new NormalizedMacroObservationDto(p.date(), v.doubleValue()));
        }
        out.sort(Comparator.comparing(NormalizedMacroObservationDto::date));
        return out;
    }

    private List<NormalizedMacroObservationDto> loadIndexObservations(
            String logicalKey,
            String evdsSeriesCode,
            LocalDate fromInclusive,
            LocalDate toInclusive
    ) {
        InflationIndicatorType type = switch (logicalKey) {
            case EvdsSeriesLogicalNames.CPI_TR_INDEX -> InflationIndicatorType.CPI;
            case EvdsSeriesLogicalNames.PPI_TR_INDEX -> InflationIndicatorType.PPI;
            default -> null;
        };
        if (type == null) {
            return loadEvdsOnly(evdsSeriesCode, logicalKey, fromInclusive, toInclusive);
        }
        List<EvdsSeriesPoint> dbPts = inflationIndexQueryService.indexPointsBetween(type, fromInclusive, toInclusive);
        return toObservationsFromPoints(dbPts);
    }

    /** Politika / fonlama için ayrı DB tablosu yok — tek EVDS aralığı. */
    private List<NormalizedMacroObservationDto> loadEvdsOnly(
            String evdsSeriesCode,
            String logicalKey,
            LocalDate fromInclusive,
            LocalDate toInclusive
    ) {
        return mergeWithEvdsGap(evdsSeriesCode, logicalKey, fromInclusive, toInclusive, List.of(), false);
    }

    private List<NormalizedMacroObservationDto> mergeWithEvdsGap(
            String evdsSeriesCode,
            String logicalKey,
            LocalDate fromInclusive,
            LocalDate toInclusive,
            List<NormalizedMacroObservationDto> dbObs,
            boolean monthlySeries
    ) {
        if (dbObs == null || dbObs.isEmpty()) {
            log.info("[MACRO_PANEL] evds_full logicalKey={} seriesCode={}", logicalKey, evdsSeriesCode);
            return toObservations(evdsSeriesCode, fromInclusive, toInclusive);
        }
        LocalDate dbLast = parseObservationDate(dbObs.getLast().date());
        if (dbLast == null) {
            return toObservations(evdsSeriesCode, fromInclusive, toInclusive);
        }
        if (monthlySeries) {
            YearMonth dbYm = YearMonth.from(dbLast);
            YearMonth toYm = YearMonth.from(toInclusive);
            if (!dbYm.isBefore(toYm)) {
                log.info(
                        "[MACRO_PANEL] db_hit_monthly logicalKey={} seriesCode={} points={} lastMonth={}",
                        logicalKey,
                        evdsSeriesCode,
                        dbObs.size(),
                        dbYm);
                return dbObs;
            }
            LocalDate gapFrom = dbYm.plusMonths(1).atDay(1);
            if (gapFrom.isBefore(fromInclusive)) {
                gapFrom = fromInclusive.withDayOfMonth(1);
            }
            if (gapFrom.isAfter(toInclusive)) {
                return dbObs;
            }
            log.info(
                    "[MACRO_PANEL] db_gap_fill_monthly logicalKey={} seriesCode={} dbPoints={} gapFrom={} to={}",
                    logicalKey,
                    evdsSeriesCode,
                    dbObs.size(),
                    gapFrom,
                    toInclusive);
            List<NormalizedMacroObservationDto> gapObs = toObservations(evdsSeriesCode, gapFrom, toInclusive);
            return mergeSorted(dbObs, gapObs);
        }
        if (!dbLast.isBefore(toInclusive)) {
            log.info(
                    "[MACRO_PANEL] db_hit logicalKey={} seriesCode={} points={} lastDate={}",
                    logicalKey,
                    evdsSeriesCode,
                    dbObs.size(),
                    dbLast);
            return dbObs;
        }
        LocalDate gapFrom = dbLast.plusDays(1);
        if (gapFrom.isBefore(fromInclusive)) {
            gapFrom = fromInclusive;
        }
        if (gapFrom.isAfter(toInclusive)) {
            return dbObs;
        }
        log.info(
                "[MACRO_PANEL] db_gap_fill logicalKey={} seriesCode={} gapFrom={} to={}",
                logicalKey,
                evdsSeriesCode,
                gapFrom,
                toInclusive);
        List<NormalizedMacroObservationDto> gapObs = toObservations(evdsSeriesCode, gapFrom, toInclusive);
        return mergeSorted(dbObs, gapObs);
    }

    private static LocalDate parseObservationDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(isoDate.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<NormalizedMacroObservationDto> mergeSorted(
            List<NormalizedMacroObservationDto> a,
            List<NormalizedMacroObservationDto> b
    ) {
        Map<String, NormalizedMacroObservationDto> map = new LinkedHashMap<>();
        for (NormalizedMacroObservationDto o : a) {
            if (o != null && o.date() != null) {
                map.put(o.date(), o);
            }
        }
        for (NormalizedMacroObservationDto o : b) {
            if (o != null && o.date() != null) {
                map.put(o.date(), o);
            }
        }
        return map.values().stream()
                .sorted(Comparator.comparing(NormalizedMacroObservationDto::date))
                .toList();
    }

    private List<NormalizedMacroObservationDto> toObservationsFromPoints(List<EvdsSeriesPoint> pts) {
        List<NormalizedMacroObservationDto> out = new ArrayList<>();
        for (EvdsSeriesPoint p : pts) {
            if (p == null || p.asOf() == null) {
                continue;
            }
            BigDecimal v = p.value();
            if (v == null) {
                continue;
            }
            LocalDate d = p.asOf().toLocalDate();
            out.add(new NormalizedMacroObservationDto(d.toString(), v.doubleValue()));
        }
        out.sort(Comparator.comparing(NormalizedMacroObservationDto::date));
        return out;
    }

    private List<NormalizedMacroObservationDto> toObservations(String evdsSeriesCode, LocalDate fromInclusive, LocalDate toInclusive) {
        try {
            List<EvdsSeriesPoint> pts = evdsDebtClient.fetchSeriesAscending(evdsSeriesCode, fromInclusive, toInclusive);
            return toObservationsFromPoints(pts);
        } catch (Exception ex) {
            log.warn(
                    "[MACRO_PANEL] evds_fetch_failed series={} range={}..{} msg={}",
                    evdsSeriesCode,
                    fromInclusive,
                    toInclusive,
                    ex.getMessage());
            return List.of();
        }
    }

    private void logSeries(String seriesCode, String logicalKey, List<NormalizedMacroObservationDto> obs, String mode) {
        String last = obs.isEmpty() ? "—" : obs.getLast().date();
        log.info(
                "[MACRO_PANEL] load mode={} logicalKey={} seriesCode={} points={} lastDate={}",
                mode,
                logicalKey,
                seriesCode,
                obs.size(),
                last);
    }

    private MacroPanelDerivedMetricsDto computeDerived(List<NormalizedMacroSeriesDto> series) {
        InflationLatestResponse infl = inflationLatestPreferDb();
        Double cpiMom = snapshotMonthlyPct(infl != null ? infl.cpi() : null, true);
        Double cpiYoy = snapshotMonthlyPct(infl != null ? infl.cpi() : null, false);
        Double ppiMom = snapshotMonthlyPct(infl != null ? infl.ppi() : null, true);
        Double ppiYoy = snapshotMonthlyPct(infl != null ? infl.ppi() : null, false);

        Double policy = lastPositivePercentFromSeries(series, EvdsSeriesLogicalNames.POLICY_RATE_TR);
        Double consumer = lastPositivePercentFromSeries(series, EvdsSeriesLogicalNames.LOAN_RATE_CONSUMER_TRY_WEEKLY);
        Double dep1m = lastPositivePercentFromSeries(series, EvdsSeriesLogicalNames.DEPOSIT_RATE_TRY_1M_WEEKLY);

        return new MacroPanelDerivedMetricsDto(
                cpiMom,
                cpiYoy,
                ppiMom,
                ppiYoy,
                sub(policy, cpiYoy),
                sub(dep1m, cpiYoy),
                sub(consumer, policy),
                sub(consumer, dep1m)
        );
    }

    private InflationLatestResponse inflationLatestPreferDb() {
        InflationIndicatorSnapshotDto cpi = inflationIndexQueryService
                .latestMetrics(InflationIndicatorType.CPI)
                .map(m -> mapMetricsSnapshot(InflationIndicatorType.CPI, m))
                .orElse(null);
        InflationIndicatorSnapshotDto ppi = inflationIndexQueryService
                .latestMetrics(InflationIndicatorType.PPI)
                .map(m -> mapMetricsSnapshot(InflationIndicatorType.PPI, m))
                .orElse(null);
        if (cpi == null && ppi == null) {
            return null;
        }
        return new InflationLatestResponse(
                cpi,
                ppi,
                "ÜFE (Yİ-ÜFE) üretici maliyet enflasyonunu; TÜFE tüketici fiyat enflasyonunu gösterir.",
                "Kaynak: EVDS (persisted index rows when available)."
        );
    }

    private InflationIndicatorSnapshotDto mapMetricsSnapshot(InflationIndicatorType type, InflationMonthMetrics m) {
        String seriesCode = inflationIndexQueryService.resolveSeriesCode(type);
        Integer baseYear = type == InflationIndicatorType.PPI
                ? inflationPpiProperties.getBaseYear()
                : inflationCpiProperties.getBaseYear();
        String note = m.annualChangePercent() == null
                ? "Yıllık kıyas için en az 13 ay endeks verisi gerekir."
                : null;
        return new InflationIndicatorSnapshotDto(
                "INFLATION",
                type.name(),
                SOURCE_EVDS,
                FREQ_MONTHLY,
                UNIT_INDEX,
                baseYear,
                "TR",
                seriesCode,
                m.monthStart(),
                m.indexValue(),
                m.monthlyChangePercent(),
                m.annualChangePercent(),
                note
        );
    }

    private static Double lastPositivePercentFromSeries(List<NormalizedMacroSeriesDto> series, String logicalKey) {
        if (series == null || logicalKey == null) {
            return null;
        }
        for (NormalizedMacroSeriesDto s : series) {
            if (!logicalKey.equals(s.logicalKey()) || s.observations() == null || s.observations().isEmpty()) {
                continue;
            }
            for (int i = s.observations().size() - 1; i >= 0; i--) {
                NormalizedMacroObservationDto o = s.observations().get(i);
                if (o == null || o.value() == null) {
                    continue;
                }
                double v = o.value();
                if (v > 0 && Double.isFinite(v)) {
                    return v;
                }
            }
        }
        return null;
    }

    private static Double snapshotMonthlyPct(InflationIndicatorSnapshotDto s, boolean mom) {
        if (s == null) {
            return null;
        }
        BigDecimal raw = mom ? s.monthlyChangePercent() : s.annualChangePercent();
        return bdDouble(raw);
    }

    private List<MacroPanelBondSummaryDto> bondSummaries() {
        try {
            return debtQueryService.latest().stream()
                    .limit(40)
                    .map(this::toBond)
                    .toList();
        } catch (Exception ex) {
            log.warn("[MACRO_PANEL] debt latest failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private MacroPanelBondSummaryDto toBond(DebtSnapshotResponse r) {
        return new MacroPanelBondSummaryDto(
                r.isin(),
                r.dirtyPrice() != null ? r.dirtyPrice().doubleValue() : null,
                r.couponRate() != null ? r.couponRate().doubleValue() : null,
                r.daysToMaturity(),
                CATEGORY_BOND_PRICE_PERFORMANCE,
                UNIT_PRICE,
                Boolean.FALSE,
                "PRICE_PERFORMANCE",
                Boolean.FALSE
        );
    }

    private static Double bdDouble(BigDecimal b) {
        return b == null ? null : b.doubleValue();
    }

    private static Double sub(Double a, Double b) {
        if (a == null || b == null) {
            return null;
        }
        double r = a - b;
        if (!Double.isFinite(r)) {
            return null;
        }
        return r;
    }
}
