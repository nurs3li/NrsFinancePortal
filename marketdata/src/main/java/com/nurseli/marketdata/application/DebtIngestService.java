package com.nurseli.marketdata.application;

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
import java.util.Comparator;
import java.util.List;
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

    @Transactional
    public void ingestLatest() {
        List<SeedDebt> externalRows = debtMarketClient.fetchLatest().stream()
                .map(r -> new SeedDebt(
                        r.isin(),
                        r.name(),
                        r.issuer(),
                        r.maturityDate(),
                        r.dirtyPrice(),
                        r.yieldPct(),
                        r.source(),
                        r.asOf(),
                        false
                )).toList();
        List<SeedDebt> evdsRows = evdsDebtClient.fetchLatest().stream()
                .map(r -> new SeedDebt(
                        r.isin(),
                        r.name(),
                        r.issuer(),
                        r.maturityDate(),
                        r.dirtyPrice(),
                        r.yieldPct(),
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

            DebtSnapshot snapshot = new DebtSnapshot();
            snapshot.setIsin(instrument.getIsin());
            snapshot.setDirtyPrice(s.dirtyPrice());
            snapshot.setYieldPct(s.yieldPct());
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
                new SeedDebt("TRT010531T16", "TR Hazine Bonosu 2031", "Hazine", "2031-05-01", new BigDecimal("94.22"), new BigDecimal("38.40"), "DEBT_MVP", now, true),
                new SeedDebt("TRT120228T10", "TR Hazine Tahvili 2028", "Hazine", "2028-02-12", new BigDecimal("97.10"), new BigDecimal("34.15"), "DEBT_MVP", now, true)
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

    private record SeedDebt(
            String isin,
            String name,
            String issuer,
            String maturityDate,
            BigDecimal dirtyPrice,
            BigDecimal yieldPct,
            String source,
            LocalDateTime asOf,
            boolean synthetic
    ) {}
}
