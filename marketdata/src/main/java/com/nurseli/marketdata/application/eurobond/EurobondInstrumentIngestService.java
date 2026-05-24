package com.nurseli.marketdata.application.eurobond;

import com.nurseli.marketdata.config.ConditionalOnEurobondEvds;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import com.nurseli.marketdata.domain.eurobond.EurobondInstrument;
import com.nurseli.marketdata.domain.eurobond.EurobondPriceSnapshot;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import com.nurseli.marketdata.infrastructure.persistence.EurobondInstrumentRepository;
import com.nurseli.marketdata.infrastructure.persistence.EurobondPriceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@ConditionalOnEurobondEvds
@RequiredArgsConstructor
@Slf4j
public class EurobondInstrumentIngestService {

    private final EurobondEvdsProperties properties;
    private final EurobondInstrumentRegistry registry;
    private final EurobondInstrumentRepository instrumentRepository;
    private final EurobondPriceSnapshotRepository snapshotRepository;
    private final EvdsDebtClient evdsDebtClient;
    private final EurobondQuoteFileLoader quoteFileLoader;

    @Transactional
    public EurobondInstrumentIngestResult syncCatalog() {
        if (!instrumentsEnabled()) {
            return new EurobondInstrumentIngestResult(0, 0, 0, "instruments-disabled");
        }
        LocalDateTime now = LocalDateTime.now();
        int touched = 0;
        for (EurobondInstrumentRegistry.ResolvedInstrument spec : registry.all()) {
            touched++;
            instrumentRepository.findByIsin(spec.isin()).ifPresentOrElse(existing -> {
                existing.setName(spec.name());
                existing.setIssuer(spec.issuer());
                existing.setCurrency(spec.currency());
                existing.setCouponPct(spec.couponPct());
                existing.setMaturityDate(spec.maturityDate());
                existing.setEvdsDirtyPriceSeries(spec.dirtyPriceSeries());
                existing.setEvdsYieldSeries(spec.yieldSeries());
                existing.setActive(true);
                existing.setUpdatedAt(now);
                instrumentRepository.save(existing);
            }, () -> {
                EurobondInstrument n = new EurobondInstrument();
                n.setIsin(spec.isin());
                n.setName(spec.name());
                n.setIssuer(spec.issuer());
                n.setCurrency(spec.currency());
                n.setCouponPct(spec.couponPct());
                n.setMaturityDate(spec.maturityDate());
                n.setEvdsDirtyPriceSeries(spec.dirtyPriceSeries());
                n.setEvdsYieldSeries(spec.yieldSeries());
                n.setActive(true);
                n.setCreatedAt(now);
                n.setUpdatedAt(now);
                instrumentRepository.save(n);
            });
        }
        return new EurobondInstrumentIngestResult(touched, 0, 0, "catalog-synced");
    }

    @Transactional
    public EurobondInstrumentIngestResult ingestHistory(LocalDate fromInclusive, LocalDate toInclusive) {
        if (!instrumentsEnabled()) {
            return new EurobondInstrumentIngestResult(0, 0, 0, "instruments-disabled");
        }
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            return new EurobondInstrumentIngestResult(0, 0, 0, "invalid-range");
        }
        syncCatalog();
        int instrumentsTouched = 0;
        int upserted = 0;
        int skipped = 0;
        for (EurobondInstrumentRegistry.ResolvedInstrument spec : registry.all()) {
            instrumentsTouched++;
            List<PricePoint> points = loadPoints(spec, fromInclusive, toInclusive);
            if (points.isEmpty()) {
                skipped++;
                continue;
            }
            Map<LocalDate, EurobondPriceSnapshot> existingByDate = new HashMap<>();
            snapshotRepository.findByIsinAndAsOfDateBetweenOrderByAsOfDateAsc(
                            spec.isin(), fromInclusive, toInclusive)
                    .forEach(s -> existingByDate.put(s.getAsOfDate(), s));
            LocalDateTime now = LocalDateTime.now();
            for (PricePoint p : points) {
                if (p.asOfDate() == null || p.cleanPrice() == null) {
                    skipped++;
                    continue;
                }
                EurobondPriceSnapshot row = existingByDate.get(p.asOfDate());
                if (row == null) {
                    row = new EurobondPriceSnapshot();
                    row.setIsin(spec.isin());
                    row.setAsOfDate(p.asOfDate());
                    row.setCreatedAt(now);
                    existingByDate.put(p.asOfDate(), row);
                }
                row.setCleanPrice(p.cleanPrice());
                row.setYieldPct(p.yieldPct() != null ? p.yieldPct() : BigDecimal.ZERO);
                row.setSource(p.source());
                snapshotRepository.save(row);
                upserted++;
            }
            log.info("[EUROBOND_INSTRUMENT] isin={} points={} range={}..{}",
                    spec.isin(), points.size(), fromInclusive, toInclusive);
        }
        return new EurobondInstrumentIngestResult(instrumentsTouched, upserted, skipped, "ok");
    }

    @Transactional
    public EurobondInstrumentIngestResult ingestDefaultLookback() {
        int years = Math.max(1, properties.getInstruments().getDefaultLookbackYears());
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(years);
        return ingestHistory(from, to);
    }

    private List<PricePoint> loadPoints(
            EurobondInstrumentRegistry.ResolvedInstrument spec,
            LocalDate from,
            LocalDate to) {
        List<PricePoint> evds = loadFromEvds(spec, from, to);
        if (!evds.isEmpty()) {
            return evds;
        }
        List<PricePoint> quotes = quoteFileLoader.load(spec.isin()).stream()
                .filter(p -> !p.asOfDate().isBefore(from) && !p.asOfDate().isAfter(to))
                .map(p -> new PricePoint(p.asOfDate(), p.cleanPrice(), p.yieldPct(), p.source()))
                .toList();
        if (!quotes.isEmpty()) {
            return quotes;
        }
        return loadCuratedSnapshot(spec, from, to);
    }

    /** YAML referans fiyatı — EVDS/quote dosyası yoksa tek güncel nokta. */
    private List<PricePoint> loadCuratedSnapshot(
            EurobondInstrumentRegistry.ResolvedInstrument spec,
            LocalDate from,
            LocalDate to) {
        if (spec.referenceCleanPrice() == null || spec.referenceCleanPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(from) || today.isAfter(to)) {
            return List.of();
        }
        BigDecimal yld = spec.referenceYieldPct() != null ? spec.referenceYieldPct() : spec.couponPct();
        return List.of(new PricePoint(today, spec.referenceCleanPrice(), yld, "CURATED_MID"));
    }

    private List<PricePoint> loadFromEvds(
            EurobondInstrumentRegistry.ResolvedInstrument spec,
            LocalDate from,
            LocalDate to) {
        if (spec.dirtyPriceSeries() == null || spec.dirtyPriceSeries().isBlank()) {
            return List.of();
        }
        List<EvdsSeriesPoint> prices = evdsDebtClient.fetchSeriesAscending(spec.dirtyPriceSeries(), from, to);
        if (prices.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, BigDecimal> yieldByDate = new HashMap<>();
        if (spec.yieldSeries() != null && !spec.yieldSeries().isBlank()) {
            for (EvdsSeriesPoint y : evdsDebtClient.fetchSeriesAscending(spec.yieldSeries(), from, to)) {
                if (y != null && y.asOf() != null && y.value() != null) {
                    yieldByDate.put(y.asOf().toLocalDate(), normalize(y.value(), spec.yieldScale()));
                }
            }
        }
        List<PricePoint> out = new ArrayList<>();
        for (EvdsSeriesPoint p : prices) {
            if (p == null || p.asOf() == null || p.value() == null) {
                continue;
            }
            LocalDate d = p.asOf().toLocalDate();
            BigDecimal price = normalize(p.value(), spec.dirtyPriceScale());
            BigDecimal yield = yieldByDate.getOrDefault(d, spec.couponPct());
            out.add(new PricePoint(d, price, yield, "EVDS"));
        }
        return out;
    }

    private BigDecimal normalize(BigDecimal value, BigDecimal scale) {
        if (value == null) {
            return null;
        }
        if (scale == null || scale.compareTo(BigDecimal.ZERO) <= 0 || BigDecimal.ONE.compareTo(scale) == 0) {
            return value;
        }
        return value.divide(scale, 6, RoundingMode.HALF_UP);
    }

    private boolean instrumentsEnabled() {
        return properties.isEnabled() && properties.getInstruments().isEnabled();
    }

    public record PricePoint(LocalDate asOfDate, BigDecimal cleanPrice, BigDecimal yieldPct, String source) {}

    public record EurobondInstrumentIngestResult(
            int instrumentsTouched, int pointsUpserted, int pointsSkipped, String status) {}
}
