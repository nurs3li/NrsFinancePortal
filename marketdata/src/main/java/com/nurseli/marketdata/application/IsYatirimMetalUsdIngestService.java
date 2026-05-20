package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.isyatirim.commodity.IsYatirimCommodityClient;
import com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopHistoricalParser;
import com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopHistoricalParser.ChartRow;
import com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopHistoricalParser.ParsedHistorical;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class IsYatirimMetalUsdIngestService {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    private final MarketMetalsIsyatirimProperties properties;
    private final IsYatirimCommodityClient commodityClient;
    private final IsYatirimViopHistoricalParser chartParser;
    private final MarketPriceHistoryRepository repository;

    public record IngestSummary(
            int inserted,
            int skippedDuplicate,
            int ignoredOutOfRange,
            int parsedPoints
    ) {}

    /**
     * Admin backfill — sembol bazında hata izole; dış kaynak hatası yukarı fırlatılmaz, özet loglanır.
     */
    @Transactional
    public IngestSummary ingestRange(
            PreciousMetalUsdCatalog.Entry entry,
            LocalDate fromInclusive,
            LocalDate toInclusive,
            boolean force
    ) {
        if (!properties.isEnabled()) {
            log.warn("ISYATIRIM_METAL_BACKFILL_SKIPPED reason=disabled symbol={}", entry.canonicalSymbol());
            return new IngestSummary(0, 0, 0, 0);
        }
        LocalDateTime reqFrom = fromInclusive.atStartOfDay(IST).toLocalDateTime();
        LocalDateTime reqTo = toInclusive.atTime(23, 59, 59);
        try {
            String body = commodityClient.fetchHistoricalChart(
                    entry.providerSymbol(),
                    reqFrom,
                    reqTo,
                    properties.getPeriodMinutes());
            ParsedHistorical parsed = chartParser.parse(body);
            return persistParsed(entry.canonicalSymbol(), reqFrom, reqTo, parsed, force);
        } catch (Exception e) {
            log.warn(
                    "ISYATIRIM_METAL_INGEST_FAILED symbol={} provider={} error={}",
                    entry.canonicalSymbol(),
                    entry.providerSymbol(),
                    e.getMessage());
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e);
        }
    }

    /**
     * Scheduler: kısa lookback ile son günlük noktayı yazar; DB'de daha yeni veya aynı timestamp varsa atlar.
     */
    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void refreshLatestAll() {
        if (!properties.isEnabled() || !properties.getLatestRefresh().isEnabled()) {
            return;
        }
        int ok = 0;
        int skipped = 0;
        for (PreciousMetalUsdCatalog.Entry e : PreciousMetalUsdCatalog.all()) {
            try {
                boolean wrote = refreshLatestOne(e);
                if (wrote) {
                    ok++;
                } else {
                    skipped++;
                }
            } catch (Exception ex) {
                log.warn(
                        "ISYATIRIM_METAL_LATEST_REFRESH_FAILED symbol={} error={}",
                        e.canonicalSymbol(),
                        ex.getMessage());
            }
        }
        log.info(
                "ISYATIRIM_METAL_LATEST_REFRESH_COMPLETED event=ISYATIRIM_METAL_LATEST_REFRESH_COMPLETED wroteSymbolsApprox={} notes=per_symbol_errors_logged",
                ok);
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void refreshLatestForSymbol(PreciousMetalUsdCatalog.Entry entry) {
        if (entry == null || !properties.isEnabled()) {
            return;
        }
        refreshLatestOne(entry);
    }

    private boolean refreshLatestOne(PreciousMetalUsdCatalog.Entry entry) {
        LocalDate today = LocalDate.now(IST);
        LocalDateTime to = today.atTime(23, 59, 59);
        ParsedHistorical parsed = tryFetchShortLookback(entry, today, properties.getLatestLookbackDays());
        if (parsed.rows().isEmpty()) {
            parsed = tryFetchShortLookback(entry, today, properties.getLatestFallbackLookbackDays());
        }
        if (parsed.rows().isEmpty()) {
            log.warn("ISYATIRIM_METAL_LATEST_EMPTY symbol={}", entry.canonicalSymbol());
            return false;
        }
        ChartRow last = parsed.rows().get(parsed.rows().size() - 1);
        LocalDateTime barTs = barTimestamp(last.millis());
        LocalDateTime reqFrom = today.minusDays(properties.getLatestFallbackLookbackDays()).atStartOfDay(IST).toLocalDateTime();
        if (barTs.isBefore(reqFrom) || barTs.isAfter(to)) {
            return false;
        }
        BigDecimal price = last.price();
        if (price == null || price.signum() <= 0) {
            return false;
        }
        var existing = repository.findTopBySymbolAndSourceOrderByTimestampDesc(
                entry.canonicalSymbol(),
                PreciousMetalUsdCatalog.SOURCE);
        if (existing.isPresent() && !barTs.isAfter(existing.get().getTimestamp())) {
            return false;
        }
        if (repository.findBySymbolAndSourceAndTimestamp(entry.canonicalSymbol(), PreciousMetalUsdCatalog.SOURCE, barTs)
                .isPresent()) {
            return false;
        }
        repository.save(buildRow(entry.canonicalSymbol(), price, barTs));
        return true;
    }

    private ParsedHistorical tryFetchShortLookback(PreciousMetalUsdCatalog.Entry entry, LocalDate today, int lookbackDays) {
        LocalDateTime from = today.minusDays(lookbackDays).atStartOfDay(IST).toLocalDateTime();
        LocalDateTime to = today.atTime(23, 59, 59);
        try {
            String body = commodityClient.fetchHistoricalChart(
                    entry.providerSymbol(),
                    from,
                    to,
                    properties.getPeriodMinutes());
            return chartParser.parse(body);
        } catch (Exception e) {
            log.warn(
                    "ISYATIRIM_METAL_LATEST_FETCH_FAILED symbol={} lookbackDays={} error={}",
                    entry.canonicalSymbol(),
                    lookbackDays,
                    e.getMessage());
            return new ParsedHistorical(List.of(), null);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public IngestSummary persistParsed(
            String canonicalSymbol,
            LocalDateTime requestedFromInclusive,
            LocalDateTime requestedToInclusive,
            ParsedHistorical parsed,
            boolean force
    ) {
        int inserted = 0;
        int skippedDup = 0;
        int ignoredRange = 0;
        List<MarketPriceHistory> batch = new ArrayList<>();
        for (ChartRow row : parsed.rows()) {
            LocalDateTime barTs = barTimestamp(row.millis());
            if (barTs.isBefore(requestedFromInclusive) || barTs.isAfter(requestedToInclusive)) {
                ignoredRange++;
                continue;
            }
            BigDecimal price = row.price();
            if (price == null || price.signum() <= 0) {
                continue;
            }
            if (!force && repository
                    .findBySymbolAndSourceAndTimestamp(canonicalSymbol, PreciousMetalUsdCatalog.SOURCE, barTs)
                    .isPresent()) {
                skippedDup++;
                continue;
            }
            if (force) {
                repository.findBySymbolAndSourceAndTimestamp(canonicalSymbol, PreciousMetalUsdCatalog.SOURCE, barTs)
                        .ifPresent(repository::delete);
            }
            batch.add(buildRow(canonicalSymbol, price, barTs));
            if (batch.size() >= 400) {
                repository.saveAll(batch);
                inserted += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            repository.saveAll(batch);
            inserted += batch.size();
        }
        log.info(
                "ISYATIRIM_METAL_PERSIST_SUMMARY symbol={} parsedPoints={} inserted={} skippedDuplicate={} ignoredOutOfRange={}",
                canonicalSymbol,
                parsed.rows().size(),
                inserted,
                skippedDup,
                ignoredRange);
        return new IngestSummary(inserted, skippedDup, ignoredRange, parsed.rows().size());
    }

    private static LocalDateTime barTimestamp(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(IST).toLocalDate().atStartOfDay(IST).toLocalDateTime();
    }

    private static MarketPriceHistory buildRow(String canonicalSymbol, BigDecimal usdPerOz, LocalDateTime barTs) {
        MarketPriceHistory row = new MarketPriceHistory();
        row.setSymbol(canonicalSymbol);
        row.setBuyPrice(usdPerOz);
        row.setSellPrice(usdPerOz);
        row.setSource(PreciousMetalUsdCatalog.SOURCE);
        row.setTimestamp(barTs);
        row.setCurrency("USD");
        return row;
    }

    public Set<String> resolveBackfillSymbols(String symbolsCsv) {
        if (symbolsCsv == null || symbolsCsv.isBlank()) {
            return new LinkedHashSet<>(PreciousMetalUsdCatalog.canonicalSymbols());
        }
        Set<String> out = new LinkedHashSet<>();
        for (String p : symbolsCsv.split(",")) {
            String s = p.trim().toUpperCase();
            if (!s.isBlank() && PreciousMetalUsdCatalog.byCanonicalOrNull(s) != null) {
                out.add(s);
            }
        }
        return out;
    }
}
