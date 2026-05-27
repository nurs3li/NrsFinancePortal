package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.BatchHistoryResponse;
import com.nurseli.marketdata.api.dto.CandlePointResponse;
import com.nurseli.marketdata.api.dto.CryptoHistoryCoverageResponse;
import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.MarketType;
import com.nurseli.marketdata.api.dto.PreciousMetalUsdChanges;
import com.nurseli.marketdata.api.dto.PreciousMetalUsdOverviewRow;
import com.nurseli.marketdata.api.dto.DataQualityFlag;
import com.nurseli.marketdata.api.dto.PriceQuality;
import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.config.EquityProperties;
import com.nurseli.marketdata.config.EtfProperties;
import com.nurseli.marketdata.config.TefasProperties;
import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import com.nurseli.marketdata.domain.price.CryptoSymbolMapping;
import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import com.nurseli.marketdata.domain.price.EquityDailyCandle;
import com.nurseli.marketdata.domain.price.FxDailyCandle;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.persistence.CryptoDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.EquityDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.FxDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceBucketView;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.nurseli.marketdata.api.dto.IndicatorPointResponse;
import com.nurseli.marketdata.api.dto.MarketIndicatorsResponse;
import com.nurseli.marketdata.api.dto.TrendResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketPriceQueryService {

    // 3 ve 5: Frontend "1D" range'i icin. Equity/FX/Crypto repository'leri yalnizca daily candle
    // tuttugu icin days=1 secilince hafta sonu/tatilde sadece 1 mum gelebiliyor; lightweight-charts
    // ise >= 2 nokta istiyor. days=5 ile son 5 takvim gunu icindeki en az 2 is gunu kapanisi
    // garantilenip "Analiz grafigi icin veri bulunamadi" mesaji onlenir.
    private static final Set<Integer> ALLOWED_DAYS = Set.of(1, 3, 5, 7, 14, 30, 90, 180, 365, 730);
    private static final int MAX_SYMBOLS = 8;
    private static final BigDecimal ZERO_VOLUME = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    /**
     * Piyasa terminali 1G/1H/1A: günlük mum tablosunda boş gün kalmasın; scheduler'ın yazdığı
     * {@code market_price_history} satırlarından takvim günü bazlı OHLC ile doldurulur. 1Y+ aynı kalır.
     */
    private static final int MERGE_TICK_GAP_FILL_MAX_DAYS = 30;
    /** Boş saatlere taşınan son fiyat için tick aralığı (ör. 20:58 → 21:00 mumu). */
    private static final int HOURLY_CARRY_LOOKBACK_DAYS = 7;
    /**
     * TCMB/kripto tick ve saatlik grafik: {@code LocalDateTime.now()} JVM varsayılanında Docker'da UTC olur;
     * İstanbul duvar saati ile hem ingest hem sorgu aynı takvim saatine hizalanır.
     */
    private static final ZoneId MARKET_WALL_CLOCK_ZONE = ZoneId.of("Europe/Istanbul");

    private final MarketPriceHistoryRepository repository;
    private final CryptoDailyCandleRepository cryptoDailyCandleRepository;
    private final EquityDailyCandleRepository equityDailyCandleRepository;
    private final FxDailyCandleRepository fxDailyCandleRepository;
    private final EquityProperties equityProperties;
    private final EtfProperties etfProperties;
    private final TefasProperties tefasProperties;
    private final EquityMarketCapService equityMarketCapService;
    private final CryptoPriceIngestService cryptoPriceIngestService;
    private final MarketMetalsIsyatirimProperties marketMetalsIsyatirimProperties;
    private final PreciousMetalUsdChangeCalculator preciousMetalUsdChangeCalculator;
    private final MarketStaleTailRepairService marketStaleTailRepairService;
    private final MetalHistoryWarmupService metalHistoryWarmupService;

    public MarketPriceLatestResponse getLatestOrThrow(String symbol) {
        return repository
                .findTopBySymbolOrderByTimestampDesc(symbol)
                .map(this::mapEntityToLatest)
                .orElseThrow(() ->
                        new IllegalStateException("No data found for symbol: " + symbol)
                );
    }

    private MarketPriceLatestResponse mapEntityToLatest(MarketPriceHistory e) {
        return new MarketPriceLatestResponse(
                e.getSymbol(),
                e.getBuyPrice(),
                e.getSellPrice(),
                e.getSource(),
                e.getTimestamp(),
                e.getTimestamp(),
                PriceQuality.EXACT,
                null,
                null,
                null
        );
    }

    public Map<String, MarketPriceLatestResponse> getLatestBySource(String source) {
        return repository.findLatestBySource(source)
                .stream()
                .collect(Collectors.toMap(
                        MarketPriceHistory::getSymbol,
                        this::mapEntityToLatest,
                        (a, b) -> a.timestamp().isAfter(b.timestamp()) ? a : b,
                        LinkedHashMap::new
                ));
    }

    public Map<String, MarketPriceLatestResponse> getLatestCrypto() {
        return getLatestBySource("COINGECKO");
    }

    public Map<String, MarketPriceLatestResponse> getLatestFx() {
        return getLatestBySource("TCMB");
    }

    public Map<String, MarketPriceLatestResponse> getLatestMetals() {
        Map<String, MarketPriceLatestResponse> out = new LinkedHashMap<>();
        repository.findTopBySymbolOrderByTimestampDesc("XAU_TRY")
                .map(this::mapEntityToLatest)
                .ifPresent(row -> out.put("XAU_TRY", row));
        for (PreciousMetalUsdCatalog.Entry e : PreciousMetalUsdCatalog.all()) {
            repository.findTopBySymbolAndSourceOrderByTimestampDesc(e.canonicalSymbol(), PreciousMetalUsdCatalog.SOURCE)
                    .map(this::mapEntityToLatest)
                    .ifPresent(row -> out.put(e.canonicalSymbol(), row));
        }
        return out;
    }

    public List<PreciousMetalUsdOverviewRow> getPreciousMetalUsdOverviewPanel() {
        List<PreciousMetalUsdOverviewRow> rows = new ArrayList<>();
        for (PreciousMetalUsdCatalog.Entry e : PreciousMetalUsdCatalog.all()) {
            Optional<MarketPriceHistory> latest = repository.findTopBySymbolAndSourceOrderByTimestampDesc(
                    e.canonicalSymbol(),
                    PreciousMetalUsdCatalog.SOURCE);
            if (latest.isEmpty()) {
                continue;
            }
            MarketPriceHistory h = latest.get();
            BigDecimal mid = mid(h);
            PreciousMetalUsdChanges ch = preciousMetalUsdChangeCalculator.compute(e.canonicalSymbol());
            rows.add(new PreciousMetalUsdOverviewRow(
                    e.canonicalSymbol(),
                    e.displayName(),
                    mid,
                    ch,
                    PreciousMetalUsdCatalog.SOURCE,
                    marketMetalsIsyatirimProperties.getSourceLabel(),
                    marketMetalsIsyatirimProperties.getDelayLabel(),
                    h.getTimestamp(),
                    "USD",
                    "OUNCE"));
        }
        return rows;
    }

    public MarketPriceLatestResponse getMetalSingleLatest(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if ("XAU_TRY".equals(symbol)) {
            return getLatestOrThrow(symbol);
        }
        if (PreciousMetalUsdCatalog.isUsdOunceMetal(symbol)) {
            return repository.findTopBySymbolAndSourceOrderByTimestampDesc(symbol, PreciousMetalUsdCatalog.SOURCE)
                    .map(this::mapEntityToLatest)
                    .orElseThrow(() -> new InvalidRequestException("Bu sembol için veri bulunamadı: " + symbol));
        }
        throw new InvalidRequestException("Geçersiz metal sembolü: " + symbol);
    }

    /**
     * USD/ons kıymetli madenler — geçersiz semboller atlanır, kalanlar için günlük mum serisi döner.
     */
    public BatchHistoryResponse getPreciousMetalUsdBatchHistory(
            String rawSymbolsCsv,
            LocalDate from,
            LocalDate to,
            Integer days
    ) {
        if (rawSymbolsCsv == null || rawSymbolsCsv.isBlank()) {
            throw new InvalidRequestException("symbols zorunludur.");
        }
        List<String> symbols = Arrays.stream(rawSymbolsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();
        LocalDateTime end;
        LocalDateTime start;
        if (from != null && to != null) {
            if (to.isBefore(from)) {
                throw new InvalidRequestException("from > to");
            }
            start = from.atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
            end = to.atTime(23, 59, 59);
        } else {
            int d = days != null ? days : 730;
            validateDays(d);
            end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
            start = end.minusDays(d);
        }
        Map<String, List<CandlePointResponse>> series = new LinkedHashMap<>();
        for (String symbol : symbols) {
            if (!PreciousMetalUsdCatalog.isUsdOunceMetal(symbol)) {
                continue;
            }
            metalHistoryWarmupService.requestWarmupIfMissing(symbol, start.toLocalDate(), end.toLocalDate(), "read:precious-usd-batch");
            List<MarketPriceHistory> rows = findUsdOunceMetalRawHistory(symbol, start, end);
            series.put(symbol, toDailyCandles(rows));
        }
        return new BatchHistoryResponse(series);
    }

    public Map<String, MarketPriceLatestResponse> getLatestFunds() {
        Map<String, MarketPriceLatestResponse> fromFinnhub = getLatestBySource("ETF");
        Map<String, MarketPriceLatestResponse> fromYahoo = getLatestBySource("ETF_YAHOO");
        Map<String, MarketPriceLatestResponse> merged = new LinkedHashMap<>(fromFinnhub);
        for (var e : fromYahoo.entrySet()) {
            MarketPriceLatestResponse y = e.getValue();
            MarketPriceLatestResponse cur = merged.get(e.getKey());
            if (cur == null || y.timestamp().isAfter(cur.timestamp())) {
                merged.put(e.getKey(), y);
            }
        }
        Map<String, MarketPriceLatestResponse> fromTefas = getLatestBySource("TEFAS");
        for (var e : fromTefas.entrySet()) {
            MarketPriceLatestResponse tefas = e.getValue();
            MarketPriceLatestResponse cur = merged.get(e.getKey());
            if (cur == null || (tefas.timestamp() != null && cur.timestamp() != null && tefas.timestamp().isAfter(cur.timestamp()))) {
                merged.put(e.getKey(), tefas);
            } else if (cur == null) {
                merged.put(e.getKey(), tefas);
            }
        }
        return merged;
    }

    public Map<String, MarketPriceLatestResponse> getLatestEquity() {
        Map<String, MarketPriceLatestResponse> latest = getLatestBySource("FINHUB");
        Map<String, MarketPriceLatestResponse> out = new LinkedHashMap<>();
        for (var e : latest.entrySet()) {
            String symbol = e.getKey();
            MarketPriceLatestResponse row = e.getValue();
            EquityMarketCapInfo marketCapInfo = equityMarketCapService.getMarketCap(symbol);
            out.put(symbol, new MarketPriceLatestResponse(
                    row.symbol(),
                    row.buyPrice(),
                    row.sellPrice(),
                    row.source(),
                    row.timestamp(),
                    row.asOf(),
                    row.quality(),
                    marketCapInfo != null ? marketCapInfo.marketCapUsd() : null,
                    marketCapInfo != null ? marketCapInfo.marketCapSource() : null,
                    marketCapInfo != null ? marketCapInfo.marketCapAsOf() : null
            ));
        }
        return out;
    }

    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }
        String sym = symbol.trim().toUpperCase();
        LocalDateTime end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
        LocalDateTime start = end.minusDays(days);

        List<MarketPriceBucketView> buckets = repository.findBucketedHistory(sym, start, end);

        return buckets.stream()
                .map(b -> new MarketPriceHistoryResponse(
                        b.getBuyPrice(),
                        b.getSellPrice(),
                        b.getTimestamp(),
                        "SYSTEM",
                        b.getTimestamp(),
                        DataQualityFlag.EXACT
                ))
                .toList();
    }

    /**
     * Hisse geçmişi — yalnızca yapılandırılmış equity sembolleri (app.equity.symbols).
     */
    public List<MarketPriceHistoryResponse> getEquityHistory(String rawSymbol, int days) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (!isAllowedSymbol(MarketType.EQUITY, symbol)) {
            throw new InvalidRequestException("type=EQUITY için geçersiz symbol: " + symbol);
        }
        LocalDateTime end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
        LocalDateTime start = end.minusDays(days);
        LocalDate to = end.toLocalDate();
        marketStaleTailRepairService.repairBeforeRead(MarketType.EQUITY, symbol, to);
        List<CandlePointResponse> candles = toEquityCandles(symbol, start.toLocalDate(), to);
        if (!candles.isEmpty()) {
            if (useTickGapFillForLookbackDays(days)) {
                candles = mergeDailyPreferDailyFillGapsFromTicks(symbol, candles, start.toLocalDate(), end.toLocalDate());
            }
            return toHistoryFromCandles(candles);
        }
        return getHistory(symbol, days);
    }

    public List<MarketPriceHistoryResponse> getCryptoHistory(String rawSymbol, int days) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = normalizeCryptoSymbol(rawSymbol);
        LocalDateTime end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
        LocalDateTime start = end.minusDays(days);
        LocalDate to = end.toLocalDate();
        marketStaleTailRepairService.repairBeforeRead(MarketType.CRYPTO, symbol, to);
        ensureCryptoHistoryCoverage(symbol, start.toLocalDate(), to);
        List<CandlePointResponse> candles = toCryptoCandles(symbol, start.toLocalDate(), to);
        if (!candles.isEmpty()) {
            if (useTickGapFillForLookbackDays(days)) {
                candles = mergeDailyPreferDailyFillGapsFromTicks(symbol, candles, start.toLocalDate(), end.toLocalDate());
            }
            return toHistoryFromCandles(candles);
        }
        return getHistory(symbol, days);
    }

    /**
     * Simülasyon gibi DB-first akışlar için: yalnızca veritabanında hazır olan günlük kripto history kapsamasını döner.
     */
    public CryptoHistoryCoverageResponse getCryptoHistoryCoverage(String rawSymbol, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new InvalidRequestException("from/to geçersiz.");
        }
        String symbol = normalizeCryptoSymbol(rawSymbol);
        LocalDate today = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        LocalDate effectiveTo = to.isAfter(today) ? today : to;
        Optional<CryptoDailyCandle> oldest = cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfAsc(symbol);
        Optional<CryptoDailyCandle> newest = cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfDesc(symbol);
        long availableDays = cryptoDailyCandleRepository.countBySymbolAndAsOfBetween(symbol, from, effectiveTo);
        long expectedDays = ChronoUnit.DAYS.between(from, effectiveTo) + 1;
        boolean ready = oldest.map(CryptoDailyCandle::getAsOf).filter(day -> !day.isAfter(from)).isPresent()
                && newest.map(CryptoDailyCandle::getAsOf).filter(day -> !day.isBefore(effectiveTo)).isPresent()
                && availableDays >= expectedDays;
        return new CryptoHistoryCoverageResponse(
                symbol,
                from,
                effectiveTo,
                oldest.map(CryptoDailyCandle::getAsOf).orElse(null),
                newest.map(CryptoDailyCandle::getAsOf).orElse(null),
                availableDays,
                expectedDays,
                ready
        );
    }

    /**
     * DB-first okuma: request yolunda dış provider tetiklemeden günlük/saatlik kripto history döner.
     */
    public List<MarketPriceHistoryResponse> getPrefilledCryptoHistory(String rawSymbol, int days, String rawBucket) {
        validateDays(days);
        String bucket = normalizeBucket(rawBucket);
        validateBucketForType(MarketType.CRYPTO, bucket);
        String symbol = normalizeCryptoSymbol(rawSymbol);
        LocalDateTime end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
        LocalDateTime start = end.minusDays(days);
        if ("hourly".equals(bucket)) {
            return toHistoryFromCandles(hourlyStripFromPriceHistory(symbol, days), "SYSTEM_DB");
        }
        List<CandlePointResponse> candles = toCryptoCandles(symbol, start.toLocalDate(), end.toLocalDate());
        if (!candles.isEmpty() && useTickGapFillForLookbackDays(days)) {
            candles = mergeDailyPreferDailyFillGapsFromTicks(symbol, candles, start.toLocalDate(), end.toLocalDate());
        }
        if (candles.isEmpty()) {
            List<MarketPriceHistory> rows = repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);
            candles = toDailyCandles(rows);
        }
        return toHistoryFromCandles(candles, "SYSTEM_DB");
    }

    /**
     * Altın (XAU_TRY) geçmişi — piyasa batch grafiğiyle aynı kaynak: ham satırlar → günlük mum.
     * USD/ons sembolleri (İş Yatırım) için CoinGecko backfill çağrılmaz.
     * {@code from}/{@code to} verilirse gün aralığı; aksi halde {@code days} (izin listesinde) kullanılır.
     */
    public List<MarketPriceHistoryResponse> getMetalHistory(String rawSymbol, int days, LocalDate from, LocalDate to) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (!isAllowedSymbol(MarketType.METALS, symbol)) {
            throw new InvalidRequestException("type=METALS için geçersiz symbol: " + symbol);
        }
        if ((from == null) != (to == null)) {
            throw new InvalidRequestException("from ve to birlikte verilmelidir.");
        }
        final LocalDateTime start;
        final LocalDateTime end;
        if (from != null) {
            if (to.isBefore(from)) {
                throw new InvalidRequestException("from > to");
            }
            start = from.atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
            end = to.atTime(23, 59, 59);
        } else {
            if (days <= 0) {
                return List.of();
            }
            validateDays(days);
            end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
            start = end.minusDays(days);
        }
        LocalDate effectiveTo = from != null ? to : end.toLocalDate();
        marketStaleTailRepairService.repairBeforeRead(MarketType.METALS, symbol, effectiveTo);
        metalHistoryWarmupService.requestWarmupIfMissing(symbol, start.toLocalDate(), effectiveTo, "read:metal-history");
        List<MarketPriceHistory> rows = PreciousMetalUsdCatalog.isUsdOunceMetal(symbol)
                ? findUsdOunceMetalRawHistory(symbol, start, end)
                : repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);
        List<CandlePointResponse> candles = toDailyCandles(rows);
        if (!candles.isEmpty()) {
            return PreciousMetalUsdCatalog.isUsdOunceMetal(symbol)
                    ? toHistoryFromCandles(candles, PreciousMetalUsdCatalog.SOURCE)
                    : toHistoryFromCandles(candles);
        }
        if (from == null) {
            return getHistory(symbol, days);
        }
        return List.of();
    }

    /**
     * Döviz (USDTRY/EURTRY/GBPTRY) geçmişi — günlük mum tablosundan, gün sayısı için batch kısıtı yok.
     * Uzun aralıklı simülasyon isteklerinde {@link #getBatchHistory} çağrısı {@link #validateDays} nedeniyle
     * başarısız olabildiğinden history uç noktasında bu yol kullanılır.
     */
    public List<MarketPriceHistoryResponse> getFxHistory(String rawSymbol, int days) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (!isAllowedSymbol(MarketType.FX, symbol)) {
            throw new InvalidRequestException("type=FX için geçersiz symbol: " + symbol);
        }
        if (days <= 0) {
            return List.of();
        }
        LocalDateTime end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
        LocalDateTime start = end.minusDays(days);
        LocalDate to = end.toLocalDate();
        marketStaleTailRepairService.repairBeforeRead(MarketType.FX, symbol, to);
        List<CandlePointResponse> candles = toFxCandles(symbol, start.toLocalDate(), to);
        if (!candles.isEmpty()) {
            if (useTickGapFillForLookbackDays(days)) {
                candles = mergeDailyPreferDailyFillGapsFromTicks(symbol, candles, start.toLocalDate(), end.toLocalDate());
            }
            return toHistoryFromCandles(candles);
        }
        return getHistory(symbol, days);
    }

    // =========================
    // BATCH HISTORY (OHLC) + CACHE
    // =========================
    @Cacheable(
            cacheNames = "market:batch",
            key = "T(String).format('%s|%s|%d|%s', #rawType, #rawSymbols != null ? #rawSymbols.toString() : '', #days, #rawBucket != null ? #rawBucket : 'daily')",
            // Saatlik seri "şimdi"e göre yeniden hesaplanmalı; Redis'te zamansız anahtarla önbellek grafikleri saatlerce donduruyordu.
            condition = "#days <= 14 && (#rawBucket == null || #rawBucket.isBlank() || !#rawBucket.trim().equalsIgnoreCase(\"hourly\"))"
    )
    public BatchHistoryResponse getBatchHistory(String rawType, List<String> rawSymbols, int days, String rawBucket) {
        MarketType type = MarketType.from(rawType);
        validateDays(days);
        String bucket = normalizeBucket(rawBucket);
        validateBucketForType(type, bucket);

        List<String> symbols = normalizeAndValidateSymbols(type, rawSymbols);

        LocalDateTime end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
        LocalDateTime start = end.minusDays(days);
        LocalDate from = start.toLocalDate();
        LocalDate to = end.toLocalDate();
        boolean fillTickGaps = useTickGapFillForLookbackDays(days);

        Map<String, List<CandlePointResponse>> series = new LinkedHashMap<>();

        for (String symbol : symbols) {
            marketStaleTailRepairService.repairBeforeRead(type, symbol, to);
            if (type == MarketType.METALS) {
                metalHistoryWarmupService.requestWarmupIfMissing(symbol, from, to, "read:batch-history");
            }
            if (type == MarketType.FX) {
                if ("hourly".equals(bucket)) {
                    List<CandlePointResponse> hourly = hourlyStripFromPriceHistory(symbol, days);
                    if (hourly.size() >= 2) {
                        series.put(symbol, hourly);
                        continue;
                    }
                }
                List<CandlePointResponse> fxCandles = toFxCandles(symbol, from, to);
                if (!fxCandles.isEmpty()) {
                    series.put(symbol, fillTickGaps ? mergeDailyPreferDailyFillGapsFromTicks(symbol, fxCandles, from, to) : fxCandles);
                    continue;
                }
            }
            if (type == MarketType.CRYPTO) {
                ensureCryptoHistoryCoverage(symbol, from, to);
                if ("hourly".equals(bucket)) {
                    List<CandlePointResponse> hourly = hourlyStripFromPriceHistory(symbol, days);
                    if (hourly.size() >= 2) {
                        series.put(symbol, hourly);
                        continue;
                    }
                }
                List<CandlePointResponse> cryptoCandles = toCryptoCandles(symbol, from, to);
                if (!cryptoCandles.isEmpty()) {
                    series.put(symbol, fillTickGaps ? mergeDailyPreferDailyFillGapsFromTicks(symbol, cryptoCandles, from, to) : cryptoCandles);
                    continue;
                }
            }
            if (type == MarketType.EQUITY) {
                if ("hourly".equals(bucket)) {
                    List<CandlePointResponse> hourly = hourlyStripFromPriceHistory(symbol, days);
                    if (hourly.size() >= 2) {
                        series.put(symbol, hourly);
                        continue;
                    }
                }
                List<CandlePointResponse> equityCandles = toEquityCandles(symbol, from, to);
                if (!equityCandles.isEmpty()) {
                    series.put(symbol, fillTickGaps ? mergeDailyPreferDailyFillGapsFromTicks(symbol, equityCandles, from, to) : equityCandles);
                    continue;
                }
            }
            if (type == MarketType.METALS) {
                if ("hourly".equals(bucket)) {
                    List<CandlePointResponse> hourly = hourlyStripFromPriceHistory(symbol, days);
                    if (hourly.size() >= 2) {
                        series.put(symbol, hourly);
                        continue;
                    }
                }
            }
            List<MarketPriceHistory> rows = repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);
            series.put(symbol, toDailyCandles(rows));
        }

        return new BatchHistoryResponse(series);
    }

    private static String normalizeBucket(String rawBucket) {
        if (rawBucket == null || rawBucket.isBlank()) {
            return "daily";
        }
        return rawBucket.trim().toLowerCase(Locale.ROOT);
    }

    private void validateBucketForType(MarketType type, String bucket) {
        if ("daily".equals(bucket)) {
            return;
        }
        if ("hourly".equals(bucket)) {
            if (type != MarketType.FX
                    && type != MarketType.CRYPTO
                    && type != MarketType.EQUITY
                    && type != MarketType.METALS) {
                throw new InvalidRequestException("bucket=hourly yalnızca type=FX, CRYPTO, EQUITY veya METALS için desteklenir.");
            }
            return;
        }
        throw new InvalidRequestException("bucket yalnızca daily veya hourly olabilir.");
    }

    /**
     * {@code market_price_history} tick'lerinden saatlik mum (FX + kripto + hisse FINHUB tick + altın XAU_TRY tick).
     * <ul>
     *   <li>{@code days < 7} (1G): Tam 24 saatlik seri (seyrek tick → önceki kapanışla doldurulur).</li>
     *   <li>{@code days >= 7} (1H): Son {@code days} gün; her tick en yakın takvim saatine atanır (örn. 20:58 → 21:00 dilimi);
     *       o saatte tick yoksa son bilinen fiyat taşınır (FX, CRYPTO, EQUITY aynı mantık).</li>
     * </ul>
     */
    private List<CandlePointResponse> hourlyStripFromPriceHistory(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days < 1) {
            return List.of();
        }
        LocalDateTime end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
        if (days < 7) {
            LocalDateTime endHour = end.withMinute(0).withSecond(0).withNano(0);
            LocalDateTime queryStart = endHour.minusHours(96);
            List<CandlePointResponse> sparse = hourlyCandlesFromTicks(symbol, queryStart, end);
            if (sparse.isEmpty()) {
                sparse = hourlyCandlesFromTicks(symbol, end.minusHours(168), end);
            }
            if (sparse.isEmpty()) {
                return List.of();
            }
            return densifyHourlyLast24Hours(sparse, end);
        }
        LocalDateTime rangeStart = end.minusDays(days).withMinute(0).withSecond(0).withNano(0);
        List<CandlePointResponse> candles = hourlyCandlesFromTicks(symbol, rangeStart, end);
        if (candles.size() >= 2) {
            return candles;
        }
        LocalDateTime wider = end.minusDays(days + 2).withMinute(0).withSecond(0).withNano(0);
        candles = hourlyCandlesFromTicks(symbol, wider, end);
        return candles.size() >= 2 ? candles : List.of();
    }

    /**
     * Son 24 takvim saati (end'in bulunduğu saat dahil, geriye 23 saat) için her saatte bir mum.
     * Veri yoksa bir önceki gerçek kapanışla düz mum üretir (grafikte 24 nokta).
     */
    private List<CandlePointResponse> densifyHourlyLast24Hours(List<CandlePointResponse> sparse, LocalDateTime end) {
        if (sparse == null || sparse.isEmpty()) {
            return List.of();
        }
        List<CandlePointResponse> sorted = sparse.stream()
                .filter(Objects::nonNull)
                .filter(c -> c.t() != null)
                .sorted(Comparator.comparing(CandlePointResponse::t))
                .toList();
        if (sorted.isEmpty()) {
            return List.of();
        }
        ZoneId iz = MARKET_WALL_CLOCK_ZONE;
        LocalDateTime endHourLdt = end.withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime endHour = endHourLdt.atZone(iz).toOffsetDateTime();
        OffsetDateTime startHour = endHour.minusHours(23);
        Map<OffsetDateTime, CandlePointResponse> byHour = new TreeMap<>();
        for (CandlePointResponse c : sorted) {
            OffsetDateTime hk = c.t().truncatedTo(ChronoUnit.HOURS);
            byHour.put(hk, c);
        }
        BigDecimal carry = null;
        for (CandlePointResponse c : sorted) {
            OffsetDateTime hk = c.t().truncatedTo(ChronoUnit.HOURS);
            if (hk.isBefore(startHour)) {
                carry = c.c();
            }
        }
        if (carry == null) {
            carry = sorted.get(0).c();
        }
        List<CandlePointResponse> out = new ArrayList<>(24);
        for (OffsetDateTime h = startHour; !h.isAfter(endHour); h = h.plusHours(1)) {
            CandlePointResponse hit = byHour.get(h);
            if (hit != null) {
                carry = hit.c();
                out.add(hit);
            } else {
                out.add(CandlePointResponse.of(h, carry, carry, carry, carry, ZERO_VOLUME));
            }
        }
        return out;
    }

    /**
     * Tick zamanını en yakın takvim saatine yuvarlar (20:58 → 21:00, 20:27 → 20:00; 14:30 → 15:00).
     * 23:30–23:59 gibi yuvarlamanın ertesi güne taşmaması için aynı takvim gününde kalır (23:xx → 23:00).
     */
    private static LocalDateTime hourKeyNearest(LocalDateTime t) {
        LocalDateTime floor = t.truncatedTo(ChronoUnit.HOURS);
        if (t.isBefore(floor.plusMinutes(30))) {
            return floor;
        }
        LocalDateTime ceil = floor.plusHours(1);
        if (ceil.toLocalDate().isAfter(floor.toLocalDate())) {
            return floor;
        }
        return ceil;
    }

    private List<CandlePointResponse> hourlyCandlesFromTicks(String symbol, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (symbol == null || symbol.isBlank() || rangeStart == null || rangeEnd == null) {
            return List.of();
        }
        if (!rangeStart.isBefore(rangeEnd)) {
            return List.of();
        }
        LocalDateTime startHour = rangeStart.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endHour = rangeEnd.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime bufferStart = startHour.minusDays(HOURLY_CARRY_LOOKBACK_DAYS);
        List<MarketPriceHistory> rows = repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                symbol.trim().toUpperCase(), bufferStart, rangeEnd);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<LocalDateTime, List<MarketPriceHistory>> byHour = new TreeMap<>();
        for (MarketPriceHistory r : rows) {
            LocalDateTime t = r.getTimestamp();
            if (t == null) {
                continue;
            }
            LocalDateTime hourKey = hourKeyNearest(t);
            byHour.computeIfAbsent(hourKey, k -> new ArrayList<>()).add(r);
        }
        BigDecimal carry = null;
        for (MarketPriceHistory r : rows) {
            LocalDateTime t = r.getTimestamp();
            if (t == null) {
                continue;
            }
            if (t.isBefore(startHour)) {
                carry = mid(r);
            } else {
                break;
            }
        }
        List<CandlePointResponse> out = new ArrayList<>();
        for (LocalDateTime h = startHour; !h.isAfter(endHour); h = h.plusHours(1)) {
            List<MarketPriceHistory> hourRows = byHour.get(h);
            if (hourRows != null && !hourRows.isEmpty()) {
                hourRows.sort(Comparator.comparing(MarketPriceHistory::getTimestamp));
                BigDecimal open = mid(hourRows.get(0));
                BigDecimal close = mid(hourRows.get(hourRows.size() - 1));
                BigDecimal highAsk = hourRows.stream()
                        .map(MarketPriceHistory::getSellPrice)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(open.max(close));
                BigDecimal lowBid = hourRows.stream()
                        .map(MarketPriceHistory::getBuyPrice)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(open.min(close));
                BigDecimal high = highAsk.max(open).max(close);
                BigDecimal low = lowBid.min(open).min(close);
                out.add(CandlePointResponse.atIstanbul(
                        h,
                        open,
                        high,
                        low,
                        close,
                        deriveSyntheticVolume(hourRows, open, high, low, close)
                ));
                carry = close;
            } else if (carry != null) {
                out.add(CandlePointResponse.atIstanbul(h, carry, carry, carry, carry, ZERO_VOLUME));
            }
        }
        return out;
    }

    private void validateDays(int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new InvalidRequestException("days sadece izin verilen sabit değerlerden biri olabilir (örn. 5, 7, 30, 90, 180, 365, 730).");
        }
    }

    private List<String> normalizeAndValidateSymbols(MarketType type, List<String> rawSymbols) {
        if (rawSymbols == null || rawSymbols.isEmpty()) {
            throw new InvalidRequestException("symbols zorunludur. En az 1 sembol gönderin.");
        }

        List<String> symbols = rawSymbols.stream()
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();

        if (symbols.size() > MAX_SYMBOLS) {
            throw new InvalidRequestException("En fazla " + MAX_SYMBOLS + " sembol gönderebilirsiniz.");
        }

        List<String> invalid = symbols.stream()
                .filter(s -> !isAllowedSymbol(type, s))
                .toList();

        if (!invalid.isEmpty()) {
            throw new InvalidRequestException("type=" + type + " için geçersiz symbol(ler): " + invalid);
        }

        return symbols;
    }

    private boolean isAllowedSymbol(MarketType type, String symbol) {
        return switch (type) {
            case FX -> Set.of("USDTRY", "EURTRY", "GBPTRY").contains(symbol);
            case CRYPTO -> CryptoSymbolMapping.SYMBOL_TO_ID.containsKey(symbol);
            case METALS -> "XAU_TRY".equals(symbol) || PreciousMetalUsdCatalog.isUsdOunceMetal(symbol);
            case FUNDS -> {
                String sym = symbol != null ? symbol.toUpperCase(Locale.ROOT) : "";
                boolean etf = etfProperties != null
                        && etfProperties.getSymbols() != null
                        && etfProperties.getSymbols().stream()
                        .map(String::toUpperCase)
                        .anyMatch(s -> s.equals(sym));
                boolean tefas = tefasProperties != null
                        && tefasProperties.normalizedSymbols().contains(sym);
                yield etf || tefas;
            }
            case EQUITY -> equityProperties != null
                    && equityProperties.getSymbols() != null
                    && equityProperties.getSymbols().stream()
                    .map(String::toUpperCase)
                    .anyMatch(s -> s.equals(symbol));
        };
    }

    private String normalizeCryptoSymbol(String rawSymbol) {
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase();
        if (isAllowedSymbol(MarketType.CRYPTO, symbol)) {
            return symbol;
        }
        String maybeUsdt = symbol.endsWith("USDT") ? symbol : symbol + "USDT";
        if (isAllowedSymbol(MarketType.CRYPTO, maybeUsdt)) {
            return maybeUsdt;
        }
        throw new InvalidRequestException("type=CRYPTO için geçersiz symbol: " + symbol);
    }

    private List<CandlePointResponse> toDailyCandles(List<MarketPriceHistory> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        Map<LocalDate, List<MarketPriceHistory>> byDay = rows.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getTimestamp().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<CandlePointResponse> candles = new ArrayList<>();

        for (Map.Entry<LocalDate, List<MarketPriceHistory>> entry : byDay.entrySet()) {
            List<MarketPriceHistory> dayRows = entry.getValue();
            dayRows.sort(Comparator.comparing(MarketPriceHistory::getTimestamp));

            BigDecimal open = mid(dayRows.get(0));
            BigDecimal close = mid(dayRows.get(dayRows.size() - 1));

            BigDecimal high = dayRows.stream()
                    .map(this::mid)
                    .max(Comparator.naturalOrder())
                    .orElse(open);

            BigDecimal low = dayRows.stream()
                    .map(this::mid)
                    .min(Comparator.naturalOrder())
                    .orElse(open);

            candles.add(CandlePointResponse.atIstanbulMidnight(
                    entry.getKey(),
                    open,
                    high,
                    low,
                    close,
                    deriveSyntheticVolume(dayRows, open, high, low, close)
            ));
        }

        return candles;
    }

    private static boolean useTickGapFillForLookbackDays(int days) {
        return days > 0 && days <= MERGE_TICK_GAP_FILL_MAX_DAYS;
    }

    /**
     * Günlük mum (fx/equity/crypto tabloları) öncelikli; aralıktaki eksik takvim günleri için
     * aynı sembolün {@code market_price_history} kayıtlarından günlük OHLC üretilir (21:00 vb. toplu job).
     */
    private List<CandlePointResponse> mergeDailyPreferDailyFillGapsFromTicks(
            String symbol,
            List<CandlePointResponse> dailyPreferred,
            LocalDate fromInclusive,
            LocalDate toInclusive
    ) {
        if (symbol == null || symbol.isBlank() || fromInclusive == null || toInclusive == null || fromInclusive.isAfter(toInclusive)) {
            return dailyPreferred == null ? List.of() : dailyPreferred;
        }
        List<CandlePointResponse> daily = dailyPreferred == null ? List.of() : dailyPreferred;

        LocalDateTime rangeStart = fromInclusive.atStartOfDay();
        LocalDateTime rangeEnd = toInclusive.plusDays(1).atStartOfDay().minusNanos(1);
        List<MarketPriceHistory> raw = repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                symbol.trim().toUpperCase(), rangeStart, rangeEnd);
        List<CandlePointResponse> tickDaily = toDailyCandles(raw);
        Map<LocalDate, CandlePointResponse> tickByDay = tickDaily.stream()
                .filter(c -> c != null && c.t() != null)
                .collect(Collectors.toMap(c -> c.t().toLocalDate(), c -> c, (a, b) -> a));

        Map<LocalDate, CandlePointResponse> merged = new TreeMap<>();
        for (CandlePointResponse d : daily) {
            if (d != null && d.t() != null) {
                merged.put(d.t().toLocalDate(), d);
            }
        }
        for (LocalDate d = fromInclusive; !d.isAfter(toInclusive); d = d.plusDays(1)) {
            if (!merged.containsKey(d)) {
                CandlePointResponse t = tickByDay.get(d);
                if (t != null) {
                    merged.put(d, t);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<MarketPriceHistoryResponse> toHistoryFromCandles(List<CandlePointResponse> candles) {
        return toHistoryFromCandles(candles, "SYSTEM");
    }

    private List<MarketPriceHistoryResponse> toHistoryFromCandles(List<CandlePointResponse> candles, String responseSource) {
        return candles.stream()
                .map(c -> new MarketPriceHistoryResponse(
                        c.c(),
                        c.c(),
                        c.t().toLocalDateTime(),
                        responseSource,
                        c.t().toLocalDateTime(),
                        DataQualityFlag.EXACT
                ))
                .toList();
    }

    /**
     * USD/ons kıymetli maden günlükleri {@code IS_YATIRIM} kaynağında; aynı sembol için scheduler
     * veya sentetik tick'ler farklı {@code source} ile yazılabiliyor — geçmiş uçlarında yalnızca İş Yatırım
     * serisi okunmalı (aksi halde günlük mumda yanlış kaynak baskın çıkıyor).
     */
    private List<MarketPriceHistory> findUsdOunceMetalRawHistory(
            String symbol,
            LocalDateTime startInclusive,
            LocalDateTime endInclusive
    ) {
        LocalDate fromDay = startInclusive.toLocalDate();
        LocalDate toDay = endInclusive.toLocalDate();
        LocalDateTime qStart = fromDay.atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
        LocalDateTime endExclusive = toDay.plusDays(1).atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
        return repository.findBySymbolAndSourceAndTimestampRange(
                symbol, PreciousMetalUsdCatalog.SOURCE, qStart, endExclusive);
    }

    private List<CandlePointResponse> toEquityCandles(String symbol, LocalDate from, LocalDate to) {
        List<EquityDailyCandle> rows = equityDailyCandleRepository.findBySymbolAndAsOfBetweenOrderByAsOfAsc(symbol, from, to);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> CandlePointResponse.atIstanbulMidnight(
                        row.getAsOf(),
                        row.getOpenPrice(),
                        row.getHighPrice(),
                        row.getLowPrice(),
                        row.getClosePrice(),
                        row.getVolume()
                ))
                .toList();
    }

    private List<CandlePointResponse> toCryptoCandles(String symbol, LocalDate from, LocalDate to) {
        List<CryptoDailyCandle> rows = cryptoDailyCandleRepository.findBySymbolAndAsOfBetweenOrderByAsOfAsc(symbol, from, to);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> CandlePointResponse.atIstanbulMidnight(
                        row.getAsOf(),
                        row.getOpenPrice(),
                        row.getHighPrice(),
                        row.getLowPrice(),
                        row.getClosePrice(),
                        row.getVolume()
                ))
                .toList();
    }

    private void ensureCryptoHistoryCoverage(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null || symbol.isBlank() || from == null || to == null || to.isBefore(from)) {
            return;
        }
        Optional<CryptoDailyCandle> oldest = cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfAsc(symbol);
        if (oldest.isPresent() && !oldest.get().getAsOf().isAfter(from)) {
            return;
        }
        cryptoPriceIngestService.ensureHistoryCoverage(symbol, from, to);
    }

    private List<CandlePointResponse> toFxCandles(String symbol, LocalDate from, LocalDate to) {
        List<FxDailyCandle> rows = fxDailyCandleRepository.findBySymbolAndAsOfBetweenOrderByAsOfAsc(symbol, from, to);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> CandlePointResponse.atIstanbulMidnight(
                        row.getAsOf(),
                        row.getOpenPrice(),
                        row.getHighPrice(),
                        row.getLowPrice(),
                        row.getClosePrice(),
                        row.getVolume()
                ))
                .toList();
    }

    private BigDecimal mid(MarketPriceHistory row) {
        return row.getBuyPrice()
                .add(row.getSellPrice())
                .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal deriveSyntheticVolume(
            List<MarketPriceHistory> dayRows,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close
    ) {
        if (dayRows == null || dayRows.isEmpty()) {
            return ZERO_VOLUME;
        }
        BigDecimal maxPrice = List.of(open, high, low, close).stream()
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal minPrice = List.of(open, high, low, close).stream()
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal range = maxPrice.subtract(minPrice).abs();
        if (maxPrice.signum() <= 0) {
            return BigDecimal.valueOf(dayRows.size());
        }
        BigDecimal volatilityScore = range.divide(maxPrice, 8, RoundingMode.HALF_UP);
        BigDecimal tradeCountScore = BigDecimal.valueOf(dayRows.size());
        return tradeCountScore
                .multiply(BigDecimal.valueOf(1000))
                .multiply(BigDecimal.ONE.add(volatilityScore))
                .setScale(6, RoundingMode.HALF_UP);
    }

    // =========================
    // INDICATORS (MA + trend) + CACHE
    // =========================
    @Cacheable(
            cacheNames = "market:indicators",
            key = "#rawType + '|' + #rawSymbol + '|' + #days + '|' + (#rawMa != null ? #rawMa : '') + '|' + (#rawBucket != null ? #rawBucket : 'daily')",
            condition = "#days <= 14 && (#rawBucket == null || #rawBucket.isBlank() || !#rawBucket.trim().equalsIgnoreCase(\"hourly\"))"
    )
    public MarketIndicatorsResponse getIndicators(String rawType, String rawSymbol, int days, String rawMa, String rawBucket) {
        MarketType type = MarketType.from(rawType);
        validateDays(days);
        String bucket = normalizeBucket(rawBucket);
        validateBucketForType(type, bucket);

        String symbol = normalizeAndValidateSingleSymbol(type, rawSymbol);

        if ((type == MarketType.FX || type == MarketType.CRYPTO || type == MarketType.EQUITY || type == MarketType.METALS)
                && "hourly".equals(bucket)) {
            List<CandlePointResponse> hourly = hourlyStripFromPriceHistory(symbol, days);
            if (hourly.size() >= 2) {
                List<Integer> maWindows = parseMaWindowsCappedToSeriesLength(rawMa, hourly.size());
                return buildIndicatorsResponse(type, symbol, days, hourly, maWindows);
            }
            // Yeterli saatlik bar yoksa günlük yola düş.
        }

        LocalDateTime end = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
        LocalDateTime start = end.minusDays(days);
        if (type == MarketType.METALS) {
            marketStaleTailRepairService.repairBeforeRead(MarketType.METALS, symbol, end.toLocalDate());
            metalHistoryWarmupService.requestWarmupIfMissing(symbol, start.toLocalDate(), end.toLocalDate(), "read:indicators");
        }

        List<MarketPriceHistory> rows =
                repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);

        List<CandlePointResponse> candles = switch (type) {
            case EQUITY -> toEquityCandles(symbol, start.toLocalDate(), end.toLocalDate());
            case CRYPTO -> {
                ensureCryptoHistoryCoverage(symbol, start.toLocalDate(), end.toLocalDate());
                yield toCryptoCandles(symbol, start.toLocalDate(), end.toLocalDate());
            }
            case FX -> toFxCandles(symbol, start.toLocalDate(), end.toLocalDate());
            default -> List.of();
        };
        if (candles.isEmpty()) {
            candles = toDailyCandles(rows);
        } else if (useTickGapFillForLookbackDays(days)
                && (type == MarketType.EQUITY || type == MarketType.CRYPTO || type == MarketType.FX)) {
            candles = mergeDailyPreferDailyFillGapsFromTicks(symbol, candles, start.toLocalDate(), end.toLocalDate());
        }
        if (candles.isEmpty()) {
            throw new InvalidRequestException("Gosterge hesaplamak icin en az 1 gunluk veri gerekli.");
        }

        List<Integer> maWindows = parseMaWindowsCappedToSeriesLength(rawMa, candles.size());
        return buildIndicatorsResponse(type, symbol, days, candles, maWindows);
    }

    private MarketIndicatorsResponse buildIndicatorsResponse(
            MarketType type,
            String symbol,
            int days,
            List<CandlePointResponse> candles,
            List<Integer> maWindows
    ) {
        List<IndicatorPointResponse> closeSeries = candles.stream()
                .map(c -> new IndicatorPointResponse(c.t(), c.c()))
                .toList();

        Map<Integer, List<IndicatorPointResponse>> maSeries = buildMovingAverages(closeSeries, maWindows);
        TrendResponse trend = closeSeries.size() < 2
                ? new TrendResponse(
                        "FLAT",
                        BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
                )
                : calculateTrend(closeSeries);

        return new MarketIndicatorsResponse(type.name(), symbol, days, closeSeries, maSeries, trend);
    }

    /**
     * Saatlik mum sayısı kısa olduğunda MA pencerelerini seri uzunluğuna göre süzer (ör. 12 bar iken MA21 istenmez).
     */
    private List<Integer> parseMaWindowsCappedToSeriesLength(String rawMa, int barCount) {
        if (barCount < 2) {
            return List.of();
        }
        String value = (rawMa == null || rawMa.isBlank()) ? "7,21" : rawMa;
        List<Integer> parsed = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException ex) {
                        throw new InvalidRequestException("ma parametresi sayı olmalı. Örnek: 7,21");
                    }
                })
                .distinct()
                .sorted()
                .filter(w -> w >= 2 && w <= barCount)
                .toList();
        if (!parsed.isEmpty()) {
            return parsed;
        }
        int fallback = Math.min(5, barCount);
        if (fallback >= 2) {
            return List.of(fallback);
        }
        return List.of();
    }

    private String normalizeAndValidateSingleSymbol(MarketType type, String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (!isAllowedSymbol(type, symbol)) {
            throw new InvalidRequestException("type=" + type + " için geçersiz symbol: " + symbol);
        }
        return symbol;
    }

    private Map<Integer, List<IndicatorPointResponse>> buildMovingAverages(
            List<IndicatorPointResponse> closeSeries,
            List<Integer> windows
    ) {
        Map<Integer, List<IndicatorPointResponse>> result = new LinkedHashMap<>();

        for (Integer window : windows) {
            List<IndicatorPointResponse> points = new ArrayList<>();

            for (int i = window - 1; i < closeSeries.size(); i++) {
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = i - window + 1; j <= i; j++) {
                    sum = sum.add(closeSeries.get(j).value());
                }
                BigDecimal avg = sum.divide(BigDecimal.valueOf(window), 6, RoundingMode.HALF_UP);
                points.add(new IndicatorPointResponse(closeSeries.get(i).t(), avg));
            }

            result.put(window, points);
        }

        return result;
    }

    private TrendResponse calculateTrend(List<IndicatorPointResponse> closeSeries) {
        int n = closeSeries.size();

        double first = closeSeries.get(0).value().doubleValue();
        double[] y = new double[n];

        for (int i = 0; i < n; i++) {
            double close = closeSeries.get(i).value().doubleValue();
            y[i] = (close / first) * 100.0;
        }

        double xMean = (n - 1) / 2.0;
        double yMean = Arrays.stream(y).average().orElse(100.0);

        double num = 0.0;
        double den = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = i - xMean;
            num += dx * (y[i] - yMean);
            den += dx * dx;
        }
        double slope = den == 0.0 ? 0.0 : num / den;

        double ssTot = 0.0;
        double ssRes = 0.0;
        double intercept = yMean - slope * xMean;
        for (int i = 0; i < n; i++) {
            double pred = intercept + slope * i;
            ssTot += Math.pow(y[i] - yMean, 2);
            ssRes += Math.pow(y[i] - pred, 2);
        }
        double r2 = ssTot == 0.0 ? 0.0 : Math.max(0.0, 1.0 - (ssRes / ssTot));

        double last = closeSeries.get(n - 1).value().doubleValue();
        double normalizedReturn = (last / first) - 1.0;

        String direction;
        if (slope > 0.03) {
            direction = "UP";
        } else if (slope < -0.03) {
            direction = "DOWN";
        } else {
            direction = "FLAT";
        }

        double returnScore = Math.min(Math.abs(normalizedReturn) / 0.10, 1.0);
        double strength = Math.max(0.0, Math.min(1.0, returnScore * 0.6 + r2 * 0.4));

        return new TrendResponse(
                direction,
                BigDecimal.valueOf(slope).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(normalizedReturn).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(strength).setScale(6, RoundingMode.HALF_UP)
        );
    }
}