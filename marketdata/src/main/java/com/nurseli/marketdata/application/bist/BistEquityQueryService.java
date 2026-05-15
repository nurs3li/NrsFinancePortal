package com.nurseli.marketdata.application.bist;

import com.nurseli.marketdata.api.dto.BistBatchHistoryResponse;
import com.nurseli.marketdata.api.dto.BistEquityCandleResponse;
import com.nurseli.marketdata.api.dto.BistEquityHistoryResponse;
import com.nurseli.marketdata.api.dto.BistEquityLatestResponse;
import com.nurseli.marketdata.api.dto.BistSymbolResponse;
import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.bist.BistDataQuality;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolMetadata;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BistEquityQueryService {

    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final BistSymbolCatalog bistSymbolCatalog;
    private final BistProperties bistProperties;
    private final BistEquityIngestService bistEquityIngestService;

    public List<BistSymbolResponse> getSymbols() {
        return bistSymbolCatalog.getAll().stream().map(BistEquityQueryService::toSymbolDto).toList();
    }

    private static BistSymbolResponse toSymbolDto(BistSymbolMetadata m) {
        return new BistSymbolResponse(
                m.symbol(),
                m.displayName(),
                m.sector(),
                m.exchange(),
                m.currency(),
                m.yahooSymbol(),
                m.assetType(),
                m.country());
    }

    public Optional<BistEquityLatestResponse> getLatest(String symbol) {
        String sym = canonical(symbol);
        requireSupported(sym);
        Optional<MarketPriceHistory> latest =
                marketPriceHistoryRepository.findTopBySymbolAndSourceOrderByTimestampDesc(
                        sym, BistEquityDailyConstants.HISTORY_SOURCE);
        return latest.map(row -> toLatest(sym, row));
    }

    /**
     * Katalogdaki sembollerin her biri için en son günlük satır (DB'de yoksa atlanır).
     * {@code market.bist.enabled=true} ve hiç satır yoksa bir kez varsayılan lookback ile ingest dener.
     */
    public List<BistEquityLatestResponse> getLatest() {
        List<BistEquityLatestResponse> out = buildLatestFromDb();
        if (out.isEmpty() && bistProperties.isEnabled()) {
            LocalDate to = LocalDate.now(BistEquityDailyConstants.IST);
            LocalDate from = to.minusYears(Math.max(1, bistProperties.getDefaultLookbackYears()));
            for (BistSymbolMetadata m : bistSymbolCatalog.getAll()) {
                try {
                    bistEquityIngestService.ingestHistory(m.symbol(), from, to);
                } catch (Exception ex) {
                    log.debug("[BIST_DAILY_QUERY] bulk latest ingest failed symbol={} reason={}", m.symbol(), ex.getMessage());
                }
            }
            out = buildLatestFromDb();
        }
        return out;
    }

    private List<BistEquityLatestResponse> buildLatestFromDb() {
        List<BistEquityLatestResponse> out = new ArrayList<>();
        for (BistSymbolMetadata m : bistSymbolCatalog.getAll()) {
            marketPriceHistoryRepository
                    .findTopBySymbolAndSourceOrderByTimestampDesc(m.symbol(), BistEquityDailyConstants.HISTORY_SOURCE)
                    .map(row -> toLatest(m.symbol(), row))
                    .ifPresent(out::add);
        }
        return out;
    }

    public List<BistEquityHistoryResponse> getHistory(String symbol, LocalDate from, LocalDate to) {
        return loadHistory(symbol, from, to, true);
    }

    public List<BistEquityCandleResponse> getCandles(String symbol, LocalDate from, LocalDate to) {
        return getHistory(symbol, from, to).stream().map(BistEquityCandleResponse::fromHistory).toList();
    }

    public BistBatchHistoryResponse getBatchHistory(List<String> symbols, LocalDate from, LocalDate to) {
        if (symbols == null || symbols.isEmpty()) {
            return new BistBatchHistoryResponse(Map.of());
        }
        List<String> canon = new ArrayList<>();
        for (String raw : symbols) {
            String c = canonical(raw);
            if (!c.isEmpty()) {
                canon.add(c);
            }
        }
        if (canon.isEmpty()) {
            return new BistBatchHistoryResponse(Map.of());
        }
        List<String> supported = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Map<String, List<BistEquityHistoryResponse>> out = new LinkedHashMap<>();
        for (String s : canon) {
            if (!bistSymbolCatalog.isSupported(s)) {
                out.put(s, List.of());
                continue;
            }
            if (seen.add(s)) {
                supported.add(s);
            }
        }
        if (supported.isEmpty()) {
            return new BistBatchHistoryResponse(out);
        }
        LocalDateTime start = from.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        if (bistProperties.isEnabled()) {
            for (String s : supported) {
                List<MarketPriceHistory> one =
                        marketPriceHistoryRepository.findBySymbolAndSourceAndTimestampRange(
                                s, BistEquityDailyConstants.HISTORY_SOURCE, start, endExclusive);
                if (one.isEmpty()) {
                    try {
                        bistEquityIngestService.ingestHistory(s, from, to);
                    } catch (Exception ex) {
                        log.debug("[BIST_DAILY_QUERY] ingest on batch miss failed symbol={} reason={}", s, ex.getMessage());
                    }
                }
            }
        }
        List<MarketPriceHistory> rows =
                marketPriceHistoryRepository.findBySymbolsAndSourceAndTimestampRange(
                        supported, BistEquityDailyConstants.HISTORY_SOURCE, start, endExclusive);
        Map<String, List<MarketPriceHistory>> bySymbol =
                rows.stream().collect(Collectors.groupingBy(MarketPriceHistory::getSymbol, LinkedHashMap::new, Collectors.toList()));
        for (String s : canon) {
            if (out.containsKey(s)) {
                continue;
            }
            List<MarketPriceHistory> list =
                    new ArrayList<>(bySymbol.getOrDefault(s, List.of()));
            list.sort(Comparator.comparing(MarketPriceHistory::getTimestamp));
            out.put(s, toHistoryBars(s, list));
        }
        return new BistBatchHistoryResponse(out);
    }

    private List<BistEquityHistoryResponse> loadHistory(String symbol, LocalDate from, LocalDate to, boolean tryIngestIfEmpty) {
        String sym = canonical(symbol);
        requireSupported(sym);
        LocalDateTime start = from.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        List<MarketPriceHistory> rows =
                new ArrayList<>(
                        marketPriceHistoryRepository.findBySymbolAndSourceAndTimestampRange(
                                sym, BistEquityDailyConstants.HISTORY_SOURCE, start, endExclusive));
        if (rows.isEmpty() && tryIngestIfEmpty && bistProperties.isEnabled()) {
            try {
                bistEquityIngestService.ingestHistory(sym, from, to);
            } catch (Exception ex) {
                log.debug("[BIST_DAILY_QUERY] ingest after empty failed symbol={} reason={}", sym, ex.getMessage());
            }
            rows =
                    new ArrayList<>(
                            marketPriceHistoryRepository.findBySymbolAndSourceAndTimestampRange(
                                    sym, BistEquityDailyConstants.HISTORY_SOURCE, start, endExclusive));
        }
        rows.sort(Comparator.comparing(MarketPriceHistory::getTimestamp));
        return toHistoryBars(sym, rows);
    }

    static List<BistEquityHistoryResponse> toHistoryBars(String symbol, List<MarketPriceHistory> asc) {
        List<BistEquityHistoryResponse> out = new ArrayList<>(asc.size());
        for (int i = 0; i < asc.size(); i++) {
            MarketPriceHistory cur = asc.get(i);
            LocalDate d = cur.getTimestamp().toLocalDate();
            BigDecimal open;
            if (i > 0) {
                BigDecimal prevClose = asc.get(i - 1).getAdjustedClose();
                open = prevClose != null ? prevClose : firstOr(cur.getAdjustedAverage(), cur.getAdjustedClose());
            } else {
                open = firstOr(cur.getAdjustedAverage(), cur.getAdjustedClose());
            }
            BigDecimal high = cur.getAdjustedHigh();
            BigDecimal low = cur.getAdjustedLow();
            BigDecimal close = cur.getAdjustedClose();
            BigDecimal volume = cur.getAdjustedVolume();
            String q = candleQuality(cur.getDataQuality());
            out.add(
                    new BistEquityHistoryResponse(
                            symbol,
                            d,
                            open,
                            high,
                            low,
                            close,
                            volume,
                            cur.getAdjustedClose(),
                            cur.getRawClose(),
                            cur.getUsdTry(),
                            cur.getBist100Value(),
                            cur.getMarketCapTry(),
                            cur.getSource(),
                            q));
        }
        return out;
    }

    private static String candleQuality(String stored) {
        if (stored == null) {
            return BistDataQuality.PARTIAL.name();
        }
        if (BistDataQuality.HISTORICAL.name().equals(stored)) {
            return BistDataQuality.PARTIAL.name();
        }
        return stored;
    }

    private static BigDecimal firstOr(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }

    private BistEquityLatestResponse toLatest(String symbol, MarketPriceHistory latest) {
        var meta = bistSymbolCatalog.findBySymbol(symbol).orElse(null);
        String display = meta != null ? meta.displayName() : symbol;
        String sector = meta != null ? meta.sector() : null;
        String currency = latest.getCurrency() != null ? latest.getCurrency() : "TRY";

        List<MarketPriceHistory> top2 =
                marketPriceHistoryRepository.findTop2BySymbolAndSourceOrderByTimestampDesc(
                        symbol, BistEquityDailyConstants.HISTORY_SOURCE);
        BigDecimal change = null;
        BigDecimal changePct = null;
        if (top2.size() >= 2) {
            MarketPriceHistory prev = top2.get(1);
            if (latest.getAdjustedClose() != null && prev.getAdjustedClose() != null) {
                change = latest.getAdjustedClose().subtract(prev.getAdjustedClose());
                if (prev.getAdjustedClose().signum() != 0) {
                    changePct =
                            change.divide(prev.getAdjustedClose(), 8, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100));
                }
            }
        }

        Instant lastUpdated = latest.getTimestamp().atZone(BistEquityDailyConstants.IST).toInstant();

        return new BistEquityLatestResponse(
                symbol,
                display,
                sector,
                currency,
                latest.getAdjustedClose(),
                latest.getRawClose(),
                change,
                changePct,
                latest.getAdjustedVolume(),
                latest.getMarketCapTry(),
                latest.getMarketCapUsd(),
                latest.getSource(),
                latest.getDataQuality(),
                lastUpdated);
    }

    private void requireSupported(String symbol) {
        if (!bistSymbolCatalog.isSupported(symbol)) {
            throw new BistEquityUnsupportedSymbolException(symbol);
        }
    }

    private static String canonical(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
