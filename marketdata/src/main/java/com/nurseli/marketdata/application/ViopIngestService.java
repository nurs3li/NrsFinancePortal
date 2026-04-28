package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.derivatives.DerivativeContract;
import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import com.nurseli.marketdata.domain.derivatives.OpenInterestSnapshot;
import com.nurseli.marketdata.infrastructure.bist.BistViopClient;
import com.nurseli.marketdata.repository.DerivativeContractRepository;
import com.nurseli.marketdata.repository.DerivativeSnapshotRepository;
import com.nurseli.marketdata.repository.OpenInterestSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ViopIngestService {

    private final DerivativeContractRepository contractRepository;
    private final DerivativeSnapshotRepository snapshotRepository;
    private final OpenInterestSnapshotRepository openInterestSnapshotRepository;
    private final BistViopClient bistViopClient;

    @Transactional
    public void ingestLatest() {
        List<SeedContract> realRows = bistViopClient.fetchLatest().stream()
                .map(r -> new SeedContract(
                        r.contractCode(),
                        r.underlying(),
                        r.expiry(),
                        r.type(),
                        r.price(),
                        r.spot(),
                        r.openInterest()
                )).toList();

        LocalDateTime now = LocalDateTime.now();
        List<SeedContract> seeds = !realRows.isEmpty()
                ? realRows
                : List.of(
                    new SeedContract("XU0300626", "XU030", "2026-06-30", "FUTURES", new BigDecimal("12400"), new BigDecimal("12335"), 145_000L),
                    new SeedContract("USDTRY0626", "USDTRY", "2026-06-30", "FUTURES", new BigDecimal("45.20"), new BigDecimal("44.95"), 92_000L)
                );

        for (SeedContract s : seeds) {
            DerivativeContract contract = contractRepository.findByContractCode(s.contractCode())
                    .orElseGet(() -> {
                        DerivativeContract c = new DerivativeContract();
                        c.setContractCode(s.contractCode());
                        c.setUnderlying(s.underlying());
                        c.setExpiry(s.expiry());
                        c.setType(s.type());
                        return contractRepository.save(c);
                    });

            DerivativeSnapshot snapshot = new DerivativeSnapshot();
            snapshot.setContractCode(contract.getContractCode());
            snapshot.setPrice(s.price());
            snapshot.setTheoreticalSpot(s.spot());
            snapshot.setSource("VIOP_MVP");
            snapshot.setAsOf(now);
            snapshotRepository.save(snapshot);

            OpenInterestSnapshot oi = new OpenInterestSnapshot();
            oi.setContractCode(contract.getContractCode());
            oi.setOpenInterest(s.openInterest());
            oi.setAsOf(now);
            openInterestSnapshotRepository.save(oi);
        }
    }

    private record SeedContract(
            String contractCode,
            String underlying,
            String expiry,
            String type,
            BigDecimal price,
            BigDecimal spot,
            Long openInterest
    ) {}
}
