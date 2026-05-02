package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.derivatives.DerivativeContract;
import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import com.nurseli.marketdata.domain.derivatives.OpenInterestSnapshot;
import com.nurseli.marketdata.config.ViopHybridProperties;
import com.nurseli.marketdata.infrastructure.bist.BistViopClient;
import com.nurseli.marketdata.repository.DerivativeContractRepository;
import com.nurseli.marketdata.repository.DerivativeSnapshotRepository;
import com.nurseli.marketdata.repository.OpenInterestSnapshotRepository;
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
public class ViopIngestService {

    private final DerivativeContractRepository contractRepository;
    private final DerivativeSnapshotRepository snapshotRepository;
    private final OpenInterestSnapshotRepository openInterestSnapshotRepository;
    private final BistViopClient bistViopClient;
    private final ViopHybridProperties viopHybridProperties;
    private final ViopHybridAggregationService viopHybridAggregationService;
    private final ViopContractParser viopContractParser;
    private final ViopTickerCacheService viopTickerCacheService;
    private final ViopVolatilityAlertPublisher viopVolatilityAlertPublisher;

    @Transactional
    public void ingestLatest() {
        if (viopHybridProperties.isEnabled()) {
            ingestHybrid();
            return;
        }
        ingestLegacy();
    }

    private void ingestLegacy() {
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
        List<SeedContract> defaults = List.of(
                new SeedContract("XU0300626", "XU030", "2026-06-30", "FUTURES", new BigDecimal("12400"), new BigDecimal("12335"), 145_000L),
                new SeedContract("USDTRY0626", "USDTRY", "2026-06-30", "FUTURES", new BigDecimal("45.20"), new BigDecimal("44.95"), 92_000L),
                new SeedContract("EURTRY0626", "EURTRY", "2026-06-30", "FUTURES", new BigDecimal("52.60"), new BigDecimal("52.35"), 61_000L),
                new SeedContract("ALTIN0626", "ALTIN", "2026-06-30", "FUTURES", new BigDecimal("3150.00"), new BigDecimal("3128.00"), 44_000L)
        );
        java.util.LinkedHashMap<String, SeedContract> merged = new java.util.LinkedHashMap<>();
        for (SeedContract r : realRows) {
            merged.put(r.contractCode(), r);
        }
        for (SeedContract d : defaults) {
            merged.putIfAbsent(d.contractCode(), d);
        }
        List<SeedContract> seeds = List.copyOf(merged.values());

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
            oi.setDailyVolume(null);
            oi.setAsOf(now);
            openInterestSnapshotRepository.save(oi);
        }
    }

    private void ingestHybrid() {
        List<ViopHybridAggregationService.HybridViopRow> rows = viopHybridAggregationService.fetchLatest();
        if (rows.isEmpty()) {
            log.warn("[VIOP_HYBRID] No merged row produced; fallback to legacy ingest");
            ingestLegacy();
            return;
        }
        for (ViopHybridAggregationService.HybridViopRow row : rows) {
            String contractCode = row.contractCode();
            if (contractCode == null || contractCode.isBlank()) {
                continue;
            }
            String normalizedExpiry = viopContractParser.inferExpiryFromContractCode(contractCode, row.expiry());
            DerivativeContract contract = contractRepository.findByContractCode(contractCode)
                    .orElseGet(() -> {
                        DerivativeContract c = new DerivativeContract();
                        c.setContractCode(contractCode);
                        c.setUnderlying(row.underlying() == null || row.underlying().isBlank() ? "UNKNOWN" : row.underlying());
                        c.setExpiry(normalizedExpiry == null ? "2099-12-31" : normalizedExpiry);
                        c.setType(row.type() == null || row.type().isBlank() ? "FUTURES" : row.type());
                        return contractRepository.save(c);
                    });

            Long previousOi = openInterestSnapshotRepository.findTopByContractCodeOrderByAsOfDesc(contractCode)
                    .map(OpenInterestSnapshot::getOpenInterest)
                    .orElse(null);

            DerivativeSnapshot snapshot = new DerivativeSnapshot();
            snapshot.setContractCode(contractCode);
            snapshot.setPrice(row.price() == null ? BigDecimal.ZERO : row.price());
            snapshot.setTheoreticalSpot(row.spot() == null ? BigDecimal.ZERO : row.spot());
            snapshot.setBasis(row.basis());
            snapshot.setMaintenanceMargin(row.maintenanceMargin());
            snapshot.setDaysToExpiry(row.daysToExpiry());
            snapshot.setDataQuality(row.quality());
            snapshot.setPriceSource(row.priceSource());
            snapshot.setPriceLatencyMs(row.priceLatencyMs());
            snapshot.setSource(row.source() == null ? "VIOP_HYBRID" : row.source());
            snapshot.setAsOf(row.asOf() == null ? LocalDateTime.now() : row.asOf());
            snapshotRepository.save(snapshot);

            OpenInterestSnapshot oi = new OpenInterestSnapshot();
            oi.setContractCode(contractCode);
            oi.setOpenInterest(row.openInterest() == null ? 0L : row.openInterest());
            oi.setDailyVolume(row.dailyVolume());
            oi.setAsOf(row.asOf() == null ? LocalDateTime.now() : row.asOf());
            openInterestSnapshotRepository.save(oi);

            viopTickerCacheService.cache(row);
            publishOpenInterestSurgeIfNeeded(contractCode, previousOi, oi.getOpenInterest());
        }
    }

    private void publishOpenInterestSurgeIfNeeded(String contractCode, Long previousOi, Long latestOi) {
        if (previousOi == null || previousOi <= 0 || latestOi == null || latestOi <= previousOi) {
            return;
        }
        double pctChange = ((double) (latestOi - previousOi) / previousOi) * 100.0;
        if (pctChange >= viopHybridProperties.getOpenInterestSurgeThresholdPct()) {
            viopVolatilityAlertPublisher.publishOpenInterestSurge(contractCode, previousOi, latestOi, pctChange);
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
