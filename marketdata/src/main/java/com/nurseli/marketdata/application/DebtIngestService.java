package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.debt.DebtInstrument;
import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import com.nurseli.marketdata.infrastructure.debt.DebtMarketClient;
import com.nurseli.marketdata.repository.DebtInstrumentRepository;
import com.nurseli.marketdata.repository.DebtSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DebtIngestService {

    private final DebtInstrumentRepository debtInstrumentRepository;
    private final DebtSnapshotRepository debtSnapshotRepository;
    private final DebtMarketClient debtMarketClient;

    @Transactional
    public void ingestLatest() {
        List<SeedDebt> realRows = debtMarketClient.fetchLatest().stream()
                .map(r -> new SeedDebt(
                        r.isin(),
                        r.name(),
                        r.issuer(),
                        r.maturityDate(),
                        r.dirtyPrice(),
                        r.yieldPct()
                )).toList();

        LocalDateTime now = LocalDateTime.now();
        List<SeedDebt> seeds = !realRows.isEmpty()
                ? realRows
                : List.of(
                    new SeedDebt("TRT010531T16", "TR Hazine Bonosu 2031", "Hazine", "2031-05-01", new BigDecimal("94.22"), new BigDecimal("38.40")),
                    new SeedDebt("TRT120228T10", "TR Hazine Tahvili 2028", "Hazine", "2028-02-12", new BigDecimal("97.10"), new BigDecimal("34.15"))
                );

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
            snapshot.setSource("DEBT_MVP");
            snapshot.setAsOf(now);
            debtSnapshotRepository.save(snapshot);
        }
    }

    private record SeedDebt(
            String isin,
            String name,
            String issuer,
            String maturityDate,
            BigDecimal dirtyPrice,
            BigDecimal yieldPct
    ) {}
}
