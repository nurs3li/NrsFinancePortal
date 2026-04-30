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
import java.util.List;

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
        List<SeedDebt> seeds = !externalRows.isEmpty()
                ? externalRows
                : !evdsRows.isEmpty()
                ? evdsRows
                : List.of(
                    new SeedDebt("TRT010531T16", "TR Hazine Bonosu 2031", "Hazine", "2031-05-01", new BigDecimal("94.22"), new BigDecimal("38.40"), "DEBT_MVP", now, true),
                    new SeedDebt("TRT120228T10", "TR Hazine Tahvili 2028", "Hazine", "2028-02-12", new BigDecimal("97.10"), new BigDecimal("34.15"), "DEBT_MVP", now, true)
                );
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
