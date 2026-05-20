package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.debt.DebtCouponFrequencyPersistence;
import com.nurseli.marketdata.domain.debt.DebtInstrument;
import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import com.nurseli.marketdata.infrastructure.debt.DebtMarketClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.repository.DebtInstrumentRepository;
import com.nurseli.marketdata.repository.DebtSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
            DebtInstrument instrument = debtInstrumentRepository.findByIsin(s.isin())
                    .orElseGet(() -> {
                        DebtInstrument i = new DebtInstrument();
                        i.setIsin(s.isin());
                        i.setName(s.name());
                        i.setIssuer(s.issuer());
                        i.setMaturityDate(s.maturityDate());
                        return debtInstrumentRepository.save(i);
                    });
            debtCouponFrequencyPersistence.ensurePersistedIfMissing(instrument);

            DebtSnapshot snapshot = new DebtSnapshot();
            snapshot.setIsin(instrument.getIsin());
            snapshot.setDirtyPrice(s.dirtyPrice() != null ? s.dirtyPrice() : BigDecimal.ZERO);
            snapshot.setYieldPct(s.couponRate() != null ? s.couponRate() : BigDecimal.ZERO);
            snapshot.setSource(resolveSource(s));
            snapshot.setAsOf(s.asOf() != null ? s.asOf() : now);
            debtSnapshotRepository.save(snapshot);
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
        List<SeedDebt> evdsRows = evdsDebtClient.fetchLatest(safeLookback).stream()
                .map(r -> new SeedDebt(
                        r.isin(),
                        r.name(),
                        r.issuer(),
                        r.maturityDate(),
                        r.dirtyPrice(),
                        r.couponRate(),
                        r.source(),
                        r.asOf(),
                        false
                ))
                .toList();
        if (evdsRows.isEmpty()) {
            log.warn("[DEBT_HISTORY_BACKFILL] No EVDS rows fetched for lookbackDays={}", safeLookback);
            return;
        }
        Map<String, List<SeedDebt>> byIsin = evdsRows.stream()
                .filter(r -> r.isin() != null && !r.isin().isBlank() && r.asOf() != null)
                .collect(Collectors.groupingBy(SeedDebt::isin));
        for (Map.Entry<String, List<SeedDebt>> entry : byIsin.entrySet()) {
            String isin = entry.getKey();
            List<SeedDebt> rows = entry.getValue().stream()
                    .sorted(Comparator.comparing(SeedDebt::asOf))
                    .toList();
            DebtInstrument instrument = debtInstrumentRepository.findByIsin(isin)
                    .orElseGet(() -> {
                        SeedDebt first = rows.get(0);
                        DebtInstrument i = new DebtInstrument();
                        i.setIsin(first.isin());
                        i.setName(first.name());
                        i.setIssuer(first.issuer());
                        i.setMaturityDate(first.maturityDate());
                        return debtInstrumentRepository.save(i);
                    });
            debtCouponFrequencyPersistence.ensurePersistedIfMissing(instrument);
            Set<LocalDateTime> existingDates = debtSnapshotRepository.findByIsinOrderByAsOfAsc(isin).stream()
                    .map(DebtSnapshot::getAsOf)
                    .collect(Collectors.toCollection(HashSet::new));
            int inserted = 0;
            for (SeedDebt s : rows) {
                if (s.asOf() == null || existingDates.contains(s.asOf())) {
                    continue;
                }
                DebtSnapshot snapshot = new DebtSnapshot();
                snapshot.setIsin(instrument.getIsin());
                snapshot.setDirtyPrice(s.dirtyPrice());
                snapshot.setYieldPct(s.couponRate() != null ? s.couponRate() : BigDecimal.ZERO);
                snapshot.setSource(resolveSource(s));
                snapshot.setAsOf(s.asOf());
                debtSnapshotRepository.save(snapshot);
                existingDates.add(s.asOf());
                inserted++;
            }
            log.info("[DEBT_HISTORY_BACKFILL] isin={} rows={} inserted={}", isin, rows.size(), inserted);
        }
    }

    private static String normIsin(String isin) {
        return isin == null ? "" : isin.trim().toUpperCase(Locale.ROOT);
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
