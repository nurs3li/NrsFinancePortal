package com.nurseli.marketdata.application.macropanel;

import com.nurseli.marketdata.api.dto.inflation.InflationIndicatorSnapshotDto;
import com.nurseli.marketdata.api.dto.inflation.InflationLatestResponse;
import com.nurseli.marketdata.api.dto.macropanel.InterestInflationMacroPanelResponse;
import com.nurseli.marketdata.api.dto.macropanel.MacroPanelBondSummaryDto;
import com.nurseli.marketdata.api.dto.macropanel.MacroPanelDerivedMetricsDto;
import com.nurseli.marketdata.api.dto.macropanel.NormalizedMacroObservationDto;
import com.nurseli.marketdata.api.dto.macropanel.NormalizedMacroSeriesDto;
import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.api.dto.PolicyRateTrResponse;
import com.nurseli.marketdata.application.deposit.DepositRateCatalog;
import com.nurseli.marketdata.application.inflation.InflationMacroService;
import com.nurseli.marketdata.application.loan.LoanRateCatalog;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.application.DebtQueryService;
import com.nurseli.marketdata.application.EvdsMacroIndicatorService;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Faiz &amp; Enflasyon paneli — EVDS + tahvil fiyat uçlarını tek normalize snapshot altında toplar.
 * Mevcut REST uçlarının davranışını değiştirmez; yalnızca okur.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MacroPanelAggregationService {

    public static final String CATEGORY_POLICY_RATE = "POLICY_RATE";
    public static final String CATEGORY_CPI_INDEX = "CPI_INDEX";
    public static final String CATEGORY_PPI_INDEX = "PPI_INDEX";
    public static final String CATEGORY_CREDIT_RATE = "CREDIT_RATE";
    public static final String CATEGORY_DEPOSIT_RATE = "DEPOSIT_RATE";
    /** Döviz cinsinden mevduat faizleri (EVDS akım %); TRY türetilmiş metriklerine dahil değildir. */
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
    private final EvdsMacroIndicatorService evdsMacroIndicatorService;
    private final InflationMacroService inflationMacroService;
    private final DebtQueryService debtQueryService;

    public InterestInflationMacroPanelResponse build() {
        String generatedAt = Instant.now().toString();
        List<String> notes = new ArrayList<>();
        notes.add(
                "Kredi faizleri borçlanma maliyetidir (EVDS akım, haftalık). Mevduat faizleri tasarruf sahibi için yaklaşık akım getiri göstergesidir.");
        notes.add(
                "TÜFE/Yİ-ÜFE serileri endeks seviyesidir; MoM/YoY türetilmiş yüzdeler derived bölümünde özetlenir.");
        notes.add(
                "Tahvil satırlarında dirty price fiyat performansıdır; listedOranPercentNotYtm YTM değildir; yapılandırılmış YTM verisi yoksa hasStructuredYieldData=false.");

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
        appendCreditSeries(series);
        appendTryDepositSeries(series);
        appendFxDepositSeries(series);

        MacroPanelDerivedMetricsDto derived = computeDerived();
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
        List<NormalizedMacroObservationDto> obs = toObservations(trimmed, start, end);
        logSeries(trimmed, logicalKey, obs);
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
        List<NormalizedMacroObservationDto> obs = toObservations(trimmed, start, end);
        logSeries(trimmed, EvdsSeriesLogicalNames.POLICY_RATE_TR, obs);
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

    private void appendCreditSeries(List<NormalizedMacroSeriesDto> out) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(120);
        for (LoanRateCatalog.LoanRateSeriesSpec spec : LoanRateCatalog.allSpecs()) {
            String code = evdsProperties.getSeriesCode(spec.evdsLogicalSeriesKey());
            if (code == null || code.isBlank()) {
                log.warn("[MACRO_PANEL] missing evds mapping logicalKey={}", spec.evdsLogicalSeriesKey());
                continue;
            }
            String trimmed = code.trim();
            List<NormalizedMacroObservationDto> obs = toObservations(trimmed, start, end);
            logSeries(trimmed, spec.evdsLogicalSeriesKey(), obs);
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
            String code = evdsProperties.getSeriesCode(spec.evdsLogicalKey());
            if (code == null || code.isBlank()) {
                log.warn("[MACRO_PANEL] missing evds mapping logicalKey={}", spec.evdsLogicalKey());
                continue;
            }
            String trimmed = code.trim();
            List<NormalizedMacroObservationDto> obs = toObservations(trimmed, start, end);
            logSeries(trimmed, spec.evdsLogicalKey(), obs);
            if (!obs.isEmpty()) {
                out.add(new NormalizedMacroSeriesDto(
                        trimmed,
                        tryDepositLabel(spec.term()),
                        CATEGORY_DEPOSIT_RATE,
                        FREQ_WEEKLY,
                        UNIT_PERCENT,
                        SOURCE_EVDS,
                        obs,
                        spec.evdsLogicalKey()
                ));
            }
        }
    }

    /**
     * USD/EUR mevduat (EVDS akım %, haftalık). TRY türetilmiş metriklerine dahil edilmez.
     * EVDS boş dönerse bile seri satırı eklenir (observations boş olabilir).
     */
    private void appendFxDepositSeries(List<NormalizedMacroSeriesDto> out) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(120);
        for (DepositRateCatalog.DepositRateSeriesSpec spec : DepositRateCatalog.allSpecs()) {
            if ("TRY".equalsIgnoreCase(spec.currency())) {
                continue;
            }
            String logicalKey = spec.evdsLogicalKey();
            String code = evdsProperties.getSeriesCode(logicalKey);
            if (code == null || code.isBlank()) {
                log.warn("[MACRO_PANEL] missing evds mapping logicalKey={}", logicalKey);
                continue;
            }
            String trimmed = code.trim();
            List<NormalizedMacroObservationDto> obs = toObservations(trimmed, start, end);
            logSeries(trimmed, logicalKey, obs);
            out.add(new NormalizedMacroSeriesDto(
                    trimmed,
                    fxDepositLabel(spec.currency(), spec.term()),
                    CATEGORY_FX_DEPOSIT_RATE,
                    FREQ_WEEKLY,
                    UNIT_PERCENT,
                    SOURCE_EVDS,
                    obs,
                    logicalKey,
                    spec.currency().toUpperCase(),
                    spec.term().toUpperCase(),
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

    private List<NormalizedMacroObservationDto> toObservations(String evdsSeriesCode, LocalDate fromInclusive, LocalDate toInclusive) {
        List<EvdsSeriesPoint> pts = evdsDebtClient.fetchSeriesAscending(evdsSeriesCode, fromInclusive, toInclusive);
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

    private void logSeries(String seriesCode, String logicalKey, List<NormalizedMacroObservationDto> obs) {
        String last = obs.isEmpty() ? "—" : obs.getLast().date();
        log.info("[MACRO_PANEL] evds_fetch logicalKey={} seriesCode={} points={} lastDate={}", logicalKey, seriesCode, obs.size(), last);
    }

    private MacroPanelDerivedMetricsDto computeDerived() {
        InflationLatestResponse infl = safeInflationLatest();
        Double cpiMom = snapshotMonthlyPct(infl != null ? infl.cpi() : null, true);
        Double cpiYoy = snapshotMonthlyPct(infl != null ? infl.cpi() : null, false);
        Double ppiMom = snapshotMonthlyPct(infl != null ? infl.ppi() : null, true);
        Double ppiYoy = snapshotMonthlyPct(infl != null ? infl.ppi() : null, false);

        Optional<PolicyRateTrResponse> policyOpt = evdsMacroIndicatorService.latestPolicyRateTr();
        Double policy = policyOpt.map(p -> bdDouble(p.valuePercent())).orElse(null);

        Double consumer = lastPercentForLogical(EvdsSeriesLogicalNames.LOAN_RATE_CONSUMER_TRY_WEEKLY);
        Double dep1m = lastPercentForLogical(EvdsSeriesLogicalNames.DEPOSIT_RATE_TRY_1M_WEEKLY);

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

    private InflationLatestResponse safeInflationLatest() {
        try {
            return inflationMacroService.latest();
        } catch (Exception ex) {
            log.warn("[MACRO_PANEL] inflation latest failed: {}", ex.getMessage());
            return null;
        }
    }

    private static Double snapshotMonthlyPct(InflationIndicatorSnapshotDto s, boolean mom) {
        if (s == null) {
            return null;
        }
        BigDecimal raw = mom ? s.monthlyChangePercent() : s.annualChangePercent();
        return bdDouble(raw);
    }

    private Double lastPercentForLogical(String logicalKey) {
        String code = evdsProperties.getSeriesCode(logicalKey);
        if (code == null || code.isBlank()) {
            return null;
        }
        LocalDate end = LocalDate.now();
        List<EvdsSeriesPoint> pts = evdsDebtClient.fetchSeriesAscending(code.trim(), end.minusWeeks(110), end);
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
        if (last == null) {
            log.info("[MACRO_PANEL] derived_fetch logicalKey={} seriesCode={} points={} lastDate=—", logicalKey, code.trim(), pts.size());
            return null;
        }
        log.info(
                "[MACRO_PANEL] derived_fetch logicalKey={} seriesCode={} points={} lastDate={}",
                logicalKey,
                code.trim(),
                pts.size(),
                last.asOf().toLocalDate()
        );
        return bdDouble(last.value());
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
                r.yieldPct() != null ? r.yieldPct().doubleValue() : null,
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
