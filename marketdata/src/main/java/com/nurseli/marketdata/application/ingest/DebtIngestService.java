package com.nurseli.marketdata.application.ingest;

import com.nurseli.marketdata.application.debt.DebtCouponFrequencyPersistence;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.domain.debt.DebtInstrument;
import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import com.nurseli.marketdata.infrastructure.debt.DebtMarketClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.persistence.DebtInstrumentRepository;
import com.nurseli.marketdata.infrastructure.persistence.DebtSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtIngestService {

    private final DebtInstrumentRepository debtInstrumentRepository;
    private final DebtSnapshotRepository debtSnapshotRepository;
    private final DebtMarketClient debtMarketClient;
    private final EvdsDebtClient evdsDebtClient;
    private final DebtCouponFrequencyPersistence debtCouponFrequencyPersistence;
    private final EvdsProperties evdsProperties;

    @Transactional
    public void ingestLatest() {
        List<SeedDebt> externalRows = debtMarketClient.fetchLatest().stream()
                .map(r -> new SeedDebt(
                        normIsin(r.isin()),
                        r.name(),
                        r.issuer(),
                        r.maturityDate(),
                        r.dirtyPrice(),
                        null,
                        r.source(),
                        r.asOf(),
                        false
                )).toList();
        List<SeedDebt> evdsRows = evdsDebtClient.fetchLatest().stream()
                .map(r -> new SeedDebt(
                        normIsin(r.isin()),
                        r.name(),
                        r.issuer(),
                        r.maturityDate(),
                        r.dirtyPrice(),
                        r.couponRate(),
                        r.source(),
                        r.asOf(),
                        false
                )).toList();

        LocalDateTime now = LocalDateTime.now();
        List<SeedDebt> seeds = selectBestSource(externalRows, evdsRows, now);
        seeds = applyFirstTimeBackfillPolicy(seeds);
        log.info("[DEBT_INGEST] externalRows={}, evdsRows={}, selectedSource={}",
                externalRows.size(),
                evdsRows.size(),
                seeds.isEmpty() ? "NONE" : resolveSource(seeds.get(0)));

        for (SeedDebt s : seeds) {
            DebtInstrument instrument = ensureInstrument(s);
            debtCouponFrequencyPersistence.ensurePersistedIfMissing(instrument);
            saveSnapshotIfMissing(instrument, s, now);
        }
    }

    private String resolveSource(SeedDebt row) {
        if (row == null) {
            return "DEBT_MVP";
        }
        if (row.synthetic()) {
            return "DEBT_MVP";
        }
        if (row.source() == null || row.source().isBlank()) {
            return "DEBT_PROVIDER";
        }
        return row.source();
    }

    private List<SeedDebt> selectBestSource(List<SeedDebt> externalRows, List<SeedDebt> evdsRows, LocalDateTime now) {
        if (!evdsRows.isEmpty() && evdsRows.size() >= externalRows.size()) {
            return evdsRows;
        }
        if (!externalRows.isEmpty()) {
            return externalRows;
        }
        if (!evdsRows.isEmpty()) {
            return evdsRows;
        }
        return List.of(
                new SeedDebt("TRT010531T16", "TR Hazine Bonosu 2031", "Hazine", "2031-05-01", new BigDecimal("94.22"), null, "DEBT_MVP", now, true),
                new SeedDebt("TRT120228T10", "TR Hazine Tahvili 2028", "Hazine", "2028-02-12", new BigDecimal("97.10"), null, "DEBT_MVP", now, true)
        );
    }

    /**
     * First fill: keep EVDS history for ISINs with no snapshots yet.
     * Subsequent runs: keep only the newest point per ISIN to avoid excessive duplicate growth.
     */
    private List<SeedDebt> applyFirstTimeBackfillPolicy(List<SeedDebt> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        String source = resolveSource(rows.get(0)).toUpperCase();
        if (!source.contains("EVDS")) {
            return rows;
        }
        Map<String, List<SeedDebt>> byIsin = rows.stream()
                .filter(r -> r.isin() != null && !r.isin().isBlank())
                .collect(Collectors.groupingBy(SeedDebt::isin));
        return byIsin.entrySet().stream()
                .flatMap(entry -> {
                    String isin = entry.getKey();
                    List<SeedDebt> sorted = entry.getValue().stream()
                            .sorted(Comparator.comparing(
                                    SeedDebt::asOf,
                                    Comparator.nullsLast(Comparator.reverseOrder())
                            ))
                            .toList();
                    int existingHistorySize = debtSnapshotRepository.findByIsinOrderByAsOfAsc(isin).size();
                    if (existingHistorySize >= 2) {
                        return sorted.stream().limit(1);
                    }
                    return sorted.stream();
                })
                .toList();
    }

    @Transactional
    public void ingestEvdsHistoryBackfill(int lookbackDays) {
        int safeLookback = Math.max(30, Math.min(lookbackDays, 730));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(safeLookback - 1L);
        int inserted = ingestHistoryForConfiguredInstruments(from, to, "startup-backfill");
        log.info("[DEBT_HISTORY_BACKFILL] periodDays={} from={} to={} inserted={}", safeLookback, from, to, inserted);
    }

    @Transactional
    public int ingestHistoryForConfiguredInstruments(LocalDate from, LocalDate to, String reason) {
        List<EvdsProperties.Instrument> instruments = configuredDebtInstruments();
        if (from == null || to == null || to.isBefore(from) || instruments.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        for (EvdsProperties.Instrument instrument : instruments) {
            inserted += ingestHistoryRange(instrument, from, to, reason);
        }
        return inserted;
    }

    @Transactional
    public int ingestHistoryRange(String isin, LocalDate from, LocalDate to, String reason) {
        EvdsProperties.Instrument instrument = configuredInstrument(isin);
        if (instrument == null) {
            log.warn("[DEBT_HISTORY] skip unknown_isin isin={} from={} to={} reason={}", isin, from, to, reason);
            return 0;
        }
        return ingestHistoryRange(instrument, from, to, reason);
    }

    private static String normIsin(String isin) {
        return isin == null ? "" : isin.trim().toUpperCase(Locale.ROOT);
    }

    private int ingestHistoryRange(EvdsProperties.Instrument instrument, LocalDate from, LocalDate to, String reason) {
        if (instrument == null || from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        List<SeedDebt> rows = evdsDebtClient.fetchInstrumentHistory(instrument, from, to).stream()
                .map(r -> new SeedDebt(
                        normIsin(r.isin()),
                        r.name(),
                        r.issuer(),
                        r.maturityDate(),
                        r.dirtyPrice(),
                        r.couponRate(),
                        r.source(),
                        r.asOf(),
                        false
                ))
                .filter(r -> r.isin() != null && !r.isin().isBlank() && r.asOf() != null)
                .sorted(Comparator.comparing(SeedDebt::asOf))
                .toList();
        if (rows.isEmpty()) {
            log.info("[DEBT_HISTORY] no_rows isin={} from={} to={} reason={}", instrument.getIsin(), from, to, reason);
            return 0;
        }
        DebtInstrument storedInstrument = ensureInstrument(rows.getFirst());
        debtCouponFrequencyPersistence.ensurePersistedIfMissing(storedInstrument);
        int inserted = 0;
        for (SeedDebt row : rows) {
            inserted += saveSnapshotIfMissing(storedInstrument, row, row.asOf());
        }
        log.info(
                "[DEBT_HISTORY] isin={} from={} to={} fetchedRows={} inserted={} reason={}",
                storedInstrument.getIsin(),
                from,
                to,
                rows.size(),
                inserted,
                reason);
        return inserted;
    }

    private List<EvdsProperties.Instrument> configuredDebtInstruments() {
        if (evdsProperties.getDebt() == null || evdsProperties.getDebt().getInstruments() == null) {
            return List.of();
        }
        return evdsProperties.getDebt().getInstruments().stream()
                .filter(i -> i != null && i.getIsin() != null && !i.getIsin().isBlank())
                .toList();
    }

    private EvdsProperties.Instrument configuredInstrument(String isin) {
        String key = normIsin(isin);
        if (key.isBlank()) {
            return null;
        }
        return configuredDebtInstruments().stream()
                .filter(i -> key.equals(normIsin(i.getIsin())))
                .findFirst()
                .orElse(null);
    }

    private DebtInstrument ensureInstrument(SeedDebt seed) {
        return debtInstrumentRepository.findByIsin(seed.isin())
                .map(existing -> {
                    existing.setName(seed.name());
                    existing.setIssuer(seed.issuer());
                    existing.setMaturityDate(seed.maturityDate());
                    return debtInstrumentRepository.save(existing);
                })
                .orElseGet(() -> {
                    DebtInstrument i = new DebtInstrument();
                    i.setIsin(seed.isin());
                    i.setName(seed.name());
                    i.setIssuer(seed.issuer());
                    i.setMaturityDate(seed.maturityDate());
                    return debtInstrumentRepository.save(i);
                });
    }

    private int saveSnapshotIfMissing(DebtInstrument instrument, SeedDebt seed, LocalDateTime defaultAsOf) {
        String source = resolveSource(seed);
        LocalDateTime asOf = seed.asOf() != null ? seed.asOf() : defaultAsOf;
        if (asOf == null || debtSnapshotRepository.existsByIsinAndSourceAndAsOf(instrument.getIsin(), source, asOf)) {
            return 0;
        }
        DebtSnapshot snapshot = new DebtSnapshot();
        snapshot.setIsin(instrument.getIsin());
        snapshot.setDirtyPrice(seed.dirtyPrice() != null ? seed.dirtyPrice() : BigDecimal.ZERO);
        snapshot.setYieldPct(seed.couponRate() != null ? seed.couponRate() : BigDecimal.ZERO);
        snapshot.setSource(source);
        snapshot.setAsOf(asOf);
        debtSnapshotRepository.save(snapshot);
        return 1;
    }

    private record SeedDebt(
            String isin,
            String name,
            String issuer,
            String maturityDate,
            BigDecimal dirtyPrice,
            /** EVDS kupon faiz oranı; DB'de yieldPct sütununda saklanır (YTM değil). */
            BigDecimal couponRate,
            String source,
            LocalDateTime asOf,
            boolean synthetic
    ) {}
}
