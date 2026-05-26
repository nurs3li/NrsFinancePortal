package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceLatestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.Duration;
import java.util.TreeMap;
import java.util.NavigableMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Market data HTTP client; güncel ve geçmiş fiyat ile CPI endeks sorguları.
 */
@Component
@RequiredArgsConstructor
public class MarketDataClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(18);
    private static final Duration HISTORY_REQUEST_TIMEOUT = Duration.ofSeconds(60);
    /** Makro panel soğuk EVDS (politika + fonlama) için finance tarafında daha uzun bekleme. */
    private static final Duration MACRO_PANEL_TIMEOUT = Duration.ofSeconds(90);

    private record ApiEnvelope<T>(Boolean success, T data, Object errors, Object meta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchHistoryBody(Map<String, List<BatchHistoryPoint>> series) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchHistoryPoint(OffsetDateTime t, BigDecimal c) {}

    /** Döviz dahil tüm latest haritalarını market-data {@code MarketPriceLatestResponse} ile aynı şema. */
    private static final ParameterizedTypeReference<Map<String, MarketPriceLatestDto>> LATEST_MAP =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<Map<String, MarketPriceLatestDto>>> LATEST_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<List<MarketPriceHistoryDto>>> HISTORY_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<BatchHistoryBody>> BATCH_HISTORY_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<List<ViopLatestRow>>> VIOP_LATEST_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<List<DebtLatestRow>>> DEBT_LATEST_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<List<DebtHistoryRow>>> DEBT_HISTORY_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<BistLatestPage>> BIST_PAGE_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<InflationHistoryBody>> CPI_HISTORY_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<TefasFundPage>> TEFAS_PAGE_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<List<TefasHistoryPoint>>> TEFAS_HISTORY_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<ViopPriceAtRow>> VIOP_PRICE_AT_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<ViopHistoryResponseRow>> VIOP_HISTORY_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<CryptoHistoryCoverageDto>> CRYPTO_COVERAGE_ENVELOPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<CryptoWarmupResponseDto>> CRYPTO_WARMUP_ENVELOPE =
            new ParameterizedTypeReference<>() {};

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InflationHistoryBody(String indicatorType, List<InflationHistoryRow> rows) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InflationHistoryRow(
            LocalDate indexMonth,
            BigDecimal indexValue) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BistLatestRow(
            String symbol,
            String displayName,
            String sector,
            BigDecimal adjustedClose,
            BigDecimal rawClose,
            BigDecimal changePercent,
            BigDecimal volume,
            String source,
            String dataQuality) {}

    private static final ParameterizedTypeReference<List<BistLatestRow>> BIST_LATEST_LIST =
            new ParameterizedTypeReference<>() {};

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BistHistRow(LocalDate date, BigDecimal close, BigDecimal adjustedClose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BistBatchBody(Map<String, List<BistHistRow>> historiesBySymbol) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BistLatestPage(
            List<BistLatestRow> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TefasFundRow(
            String code,
            String title,
            String fundType,
            Integer riskLevel,
            boolean tefasListed,
            Double price,
            Double return1d,
            Double return1w,
            Double return1m,
            Double return3m,
            Double return6m,
            Double return1y,
            Double returnYtd,
            Double return3y,
            Double return5y,
            String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TefasFundPage(
            List<TefasFundRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TefasHistoryPoint(java.time.LocalDate date, double price) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ViopLatestRow(
            String contractCode,
            String contractMonth,
            BigDecimal price,
            BigDecimal basis,
            BigDecimal marginRequirement,
            BigDecimal listPctChange1d,
            BigDecimal listPctChange7d,
            BigDecimal listPctChange30d,
            BigDecimal listPctChange365d,
            BigDecimal listPctChange14d,
            BigDecimal seqMovePct,
            Long dailyVolume,
            List<BigDecimal> sparklineCloses) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DebtLatestRow(
            String isin,
            BigDecimal dirtyPrice,
            BigDecimal yieldPct,
            BigDecimal couponRate,
            String maturityDate,
            Integer daysToMaturity,
            Boolean synthetic,
            java.time.LocalDateTime asOf,
            Integer couponFrequencyPerYear,
            String couponFrequencyLabel,
            String couponFrequencySource) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DebtHistoryRow(
            String isin,
            BigDecimal dirtyPrice,
            BigDecimal yieldPct,
            java.time.LocalDateTime asOf) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ViopPriceAtRow(
            String contractCode,
            LocalDateTime requestedDate,
            LocalDateTime matchedPriceTime,
            BigDecimal price,
            String matchType,
            String source,
            String dataQuality) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ViopHistoryPointRow(Instant time, BigDecimal price) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ViopHistoryResponseRow(String contractCode, List<ViopHistoryPointRow> points) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CryptoHistoryCoverageDto(
            String symbol,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            LocalDate oldestAvailable,
            LocalDate newestAvailable,
            long availableDays,
            long expectedDays,
            boolean ready
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CryptoWarmupResponseDto(
            String status,
            int requestedSymbols,
            int queuedSymbols,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            String message
    ) {}

    private static final ZoneId VIOP_ZONE = ZoneId.of("Europe/Istanbul");

    private final WebClient marketDataWebClient;

    @Value("${market-data.internal-backfill-token:}")
    private String internalBackfillToken;

    /**
     * Tüm "latest" haritalarını tek seferde (paralel) çeker. Portföy performansı gibi
     * çoklu sembol döngülerinde her satır için ayrı HTTP yapılmasını önler.
     */
    public LatestPricingSnapshot loadLatestPricing() {
        Mono<Map<String, MarketPriceLatestDto>> fx = latestMono("/api/market/doviz/latest");
        Mono<Map<String, MarketPriceLatestDto>> metals = latestMono("/api/market/metals/latest");
        Mono<Map<String, MarketPriceLatestDto>> crypto = latestMono("/api/market/crypto/latest");
        Mono<Map<String, MarketPriceLatestDto>> funds = latestMono("/api/market/funds/latest");
        Mono<Map<String, MarketPriceLatestDto>> equity = latestMono("/api/market/equity/latest");

        return Mono.zip(fx, metals, crypto, funds, equity)
                .map(t -> new LatestPricingSnapshot(
                        emptyMap(t.getT1()),
                        emptyMap(t.getT2()),
                        emptyMap(t.getT3()),
                        emptyMap(t.getT4()),
                        emptyMap(t.getT5())
                ))
                .timeout(REQUEST_TIMEOUT.plusSeconds(2))
                .onErrorReturn(LatestPricingSnapshot.empty())
                .block();
    }

    private Mono<Map<String, MarketPriceLatestDto>> latestMono(String uri) {
        return marketDataWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(LATEST_ENVELOPE)
                .map(envelope -> envelope.data() != null ? envelope.data() : Map.<String, MarketPriceLatestDto>of())
                .timeout(REQUEST_TIMEOUT)
                .onErrorReturn(Map.of());
    }

    private static <T> Map<String, T> emptyMap(Map<String, T> m) {
        return m != null ? m : Map.of();
    }

    private <T> Map<String, T> blockLatest(String uri, ParameterizedTypeReference<Map<String, T>> ref) {
        @SuppressWarnings("unchecked")
        Map<String, T> latest = (Map<String, T>) latestMono(uri).block(REQUEST_TIMEOUT.plusSeconds(1));
        return emptyMap(latest);
    }

    /**
     * Güncel döviz latest fiyat haritasını döner.
     */
    public Map<String, MarketPriceLatestDto> getLatestDoviz() {
        return blockLatest("/api/market/doviz/latest", LATEST_MAP);
    }

    /**
     * Güncel metal latest fiyat haritasını döner.
     */
    public Map<String, MarketPriceLatestDto> getLatestMetals() {
        return blockLatest("/api/market/metals/latest", LATEST_MAP);
    }

    /**
     * Güncel kripto latest fiyat haritasını döner.
     */
    public Map<String, MarketPriceLatestDto> getLatestCrypto() {
        return blockLatest("/api/market/crypto/latest", LATEST_MAP);
    }

    /**
     * Güncel fon latest fiyat haritasını döner.
     */
    public Map<String, MarketPriceLatestDto> getLatestFunds() {
        return blockLatest("/api/market/funds/latest", LATEST_MAP);
    }

    /**
     * Güncel hisse latest fiyat haritasını döner.
     */
    public Map<String, MarketPriceLatestDto> getLatestEquity() {
        return blockLatest("/api/market/equity/latest", LATEST_MAP);
    }

    /**
     * Varlık tipi ve sembol için TRY fiyatını çözümler (snapshot ile veya yüklemeden).
     */
    public BigDecimal getPriceTry(AssetType type, String symbol) {
        return getPriceTry(type, symbol, loadLatestPricing());
    }

    public BigDecimal getPriceTry(AssetType type, String symbol, LatestPricingSnapshot snap) {
        return switch (type) {
            case FX -> {
                MarketPriceLatestDto fx = snap.fx().get(symbol);
                yield fx != null ? nz(fx.buyPrice()) : BigDecimal.ZERO;
            }
            case CRYPTO -> {
                MarketPriceLatestDto crypto = snap.crypto().get(symbol);
                if (crypto == null) yield BigDecimal.ZERO;
                yield usdToTry(crypto.buyPrice(), snap);
            }
            case METAL -> {
                MarketPriceLatestDto metal = snap.metals().get(symbol);
                if (metal == null) yield BigDecimal.ZERO;
                BigDecimal raw = nz(metal.buyPrice());
                yield isUsdQuotedMetalSymbol(symbol) ? usdToTry(raw, snap) : raw;
            }
            case FUND -> {
                MarketPriceLatestDto fund = snap.funds().get(symbol);
                if (fund == null) yield BigDecimal.ZERO;
                yield usdToTry(fund.buyPrice(), snap);
            }
            case STOCK -> {
                MarketPriceLatestDto equity = snap.equity().get(symbol);
                if (equity == null) yield BigDecimal.ZERO;
                yield usdToTry(equity.buyPrice(), snap);
            }
            case BIST -> getBistLatestMidTry(symbol);
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal usdToTry(BigDecimal usdPrice, LatestPricingSnapshot snap) {
        if (usdPrice == null) return BigDecimal.ZERO;
        MarketPriceLatestDto usdTry = snap.fx().get("USDTRY");
        if (usdTry == null || usdTry.buyPrice() == null || usdTry.buyPrice().signum() <= 0) {
            return usdPrice;
        }
        return usdPrice.multiply(usdTry.buyPrice());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static boolean isUsdQuotedMetalSymbol(String symbol) {
        return symbol != null && symbol.trim().toUpperCase().endsWith("_USD_OZ");
    }

    /**
     * Sembol için son N gün market history DTO listesi getirir.
     */
    public List<MarketPriceHistoryDto> getHistory(AssetType type, String symbol, int days) {
        String uri = switch (type) {
            case FX -> "/api/market/doviz/history?symbol={symbol}&days={days}";
            case CRYPTO -> "/api/market/crypto/history?symbol={symbol}&days={days}";
            case METAL -> "/api/market/metals/history?symbol={symbol}&days={days}";
            case FUND -> "/api/market/funds/history?symbol={symbol}&days={days}";
            case STOCK -> "/api/market/equity/history?symbol={symbol}&days={days}";
            case BIST -> throw new IllegalStateException("BIST history: use getBistHistoryBetween(symbol, from, to)");
            default -> throw new IllegalStateException("Unsupported asset type: " + type);
        };

        List<MarketPriceHistoryDto> list = marketDataWebClient.get()
                .uri(uri, symbol, days)
                .retrieve()
                .bodyToMono(HISTORY_ENVELOPE)
                .map(envelope -> envelope.data() != null ? envelope.data() : List.<MarketPriceHistoryDto>of())
                .timeout(HISTORY_REQUEST_TIMEOUT)
                .onErrorReturn(List.of())
                .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(2));
        return list != null ? list : List.of();
    }

    public CryptoHistoryCoverageDto getCryptoHistoryCoverage(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null || symbol.isBlank() || from == null || to == null || to.isBefore(from)) {
            return new CryptoHistoryCoverageDto(symbol, from, to, null, null, 0, 0, false);
        }
        try {
            ApiEnvelope<CryptoHistoryCoverageDto> envelope = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/crypto/history/coverage")
                            .queryParam("symbol", symbol.trim().toUpperCase())
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .build())
                    .retrieve()
                    .bodyToMono(CRYPTO_COVERAGE_ENVELOPE)
                    .timeout(HISTORY_REQUEST_TIMEOUT)
                    .onErrorReturn(new ApiEnvelope<>(Boolean.FALSE, null, null, null))
                    .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(5));
            CryptoHistoryCoverageDto body = envelope != null ? envelope.data() : null;
            return body != null ? body : new CryptoHistoryCoverageDto(symbol, from, to, null, null, 0, 0, false);
        } catch (RuntimeException ignored) {
            return new CryptoHistoryCoverageDto(symbol, from, to, null, null, 0, 0, false);
        }
    }

    public boolean triggerCryptoHistoryWarmup(String symbol, LocalDate from, LocalDate to, String reason) {
        if (symbol == null || symbol.isBlank() || from == null || to == null || to.isBefore(from)) {
            return false;
        }
        try {
            ApiEnvelope<CryptoWarmupResponseDto> envelope = marketDataWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/market/backfill/crypto-history")
                            .queryParam("symbols", symbol.trim().toUpperCase())
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .queryParam("reason", reason == null || reason.isBlank() ? "simulation" : reason)
                            .build())
                    .headers(headers -> applyInternalBackfillToken(headers))
                    .retrieve()
                    .bodyToMono(CRYPTO_WARMUP_ENVELOPE)
                    .timeout(REQUEST_TIMEOUT)
                    .onErrorReturn(new ApiEnvelope<>(Boolean.FALSE, null, null, null))
                    .block(REQUEST_TIMEOUT.plusSeconds(2));
            CryptoWarmupResponseDto body = envelope != null ? envelope.data() : null;
            return body != null && ("QUEUED".equalsIgnoreCase(body.status()) || "IN_FLIGHT".equalsIgnoreCase(body.status()) || "READY".equalsIgnoreCase(body.status()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public List<MarketPriceHistoryDto> getCryptoPrefilledHistory(String symbol, int days, String bucket) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }
        List<MarketPriceHistoryDto> list = marketDataWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/market/crypto/history/prefilled")
                        .queryParam("symbol", symbol.trim().toUpperCase())
                        .queryParam("days", days)
                        .queryParam("bucket", bucket == null || bucket.isBlank() ? "daily" : bucket.trim().toLowerCase())
                        .build())
                .retrieve()
                .bodyToMono(HISTORY_ENVELOPE)
                .map(envelope -> envelope.data() != null ? envelope.data() : List.<MarketPriceHistoryDto>of())
                .timeout(HISTORY_REQUEST_TIMEOUT)
                .onErrorReturn(List.of())
                .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(2));
        return list != null ? list : List.of();
    }

    /**
     * Piyasa grafikleriyle uyumlu geçmiş — tek sembol için batch history kullanır.
     */
    public List<MarketPriceHistoryDto> getChartCompatibleHistory(AssetType type, String symbol, int days, String bucket) {
        if (!supportsBatchHistory(type) || symbol == null || symbol.isBlank()) {
            return List.of();
        }
        String marketType = batchMarketType(type);
        if (marketType == null) {
            return List.of();
        }
        String sym = symbol.trim().toUpperCase();
        try {
            ApiEnvelope<BatchHistoryBody> envelope = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/history/batch")
                            .queryParam("type", marketType)
                            .queryParam("symbols", sym)
                            .queryParam("days", days)
                            .queryParam("bucket", bucket == null || bucket.isBlank() ? "daily" : bucket.trim().toLowerCase())
                            .build())
                    .retrieve()
                    .bodyToMono(BATCH_HISTORY_ENVELOPE)
                    .timeout(HISTORY_REQUEST_TIMEOUT)
                    .onErrorReturn(new ApiEnvelope<>(Boolean.FALSE, null, null, null))
                    .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(5));
            BatchHistoryBody body = envelope != null ? envelope.data() : null;
            if (body == null || body.series() == null || body.series().isEmpty()) {
                return List.of();
            }
            List<BatchHistoryPoint> candles = body.series().get(sym);
            if ((candles == null || candles.isEmpty()) && body.series().size() == 1) {
                candles = body.series().values().stream().findFirst().orElse(List.of());
            }
            if (candles == null || candles.isEmpty()) {
                return List.of();
            }
            List<MarketPriceHistoryDto> out = new ArrayList<>(candles.size());
            ZoneId ist = ZoneId.of("Europe/Istanbul");
            for (BatchHistoryPoint candle : candles) {
                if (candle == null || candle.t() == null || candle.c() == null || candle.c().signum() <= 0) {
                    continue;
                }
                LocalDateTime ts = candle.t().atZoneSameInstant(ist).toLocalDateTime();
                out.add(new MarketPriceHistoryDto(candle.c(), candle.c(), ts));
            }
            return out;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private void applyInternalBackfillToken(org.springframework.http.HttpHeaders headers) {
        String token = resolveInternalBackfillToken();
        if (token != null && !token.isBlank()) {
            headers.set("X-Nrs-Internal-Token", token);
        }
    }

    private String resolveInternalBackfillToken() {
        if (internalBackfillToken != null && !internalBackfillToken.isBlank()) {
            return internalBackfillToken.trim();
        }
        String env = System.getenv("NRS_INTERNAL_BACKFILL_TOKEN");
        return env != null && !env.isBlank() ? env.trim() : "";
    }

    private static boolean supportsBatchHistory(AssetType type) {
        return type == AssetType.FX || type == AssetType.CRYPTO || type == AssetType.STOCK || type == AssetType.METAL;
    }

    private static String batchMarketType(AssetType type) {
        return switch (type) {
            case FX -> "FX";
            case CRYPTO -> "CRYPTO";
            case STOCK -> "EQUITY";
            case METAL -> "METALS";
            default -> null;
        };
    }

    /**
     * Metal günlük geçmiş — market-data {@code /api/market/metals/history} {@code from}/{@code to} ile.
     */
    public List<MarketPriceHistoryDto> getMetalHistoryBetween(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null || symbol.isBlank() || from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        List<MarketPriceHistoryDto> list = marketDataWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/market/metals/history")
                        .queryParam("symbol", symbol.trim().toUpperCase())
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .build())
                .retrieve()
                .bodyToMono(HISTORY_ENVELOPE)
                .map(envelope -> envelope.data() != null ? envelope.data() : List.<MarketPriceHistoryDto>of())
                .timeout(HISTORY_REQUEST_TIMEOUT)
                .onErrorReturn(List.of())
                .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(2));
        return list != null ? list : List.of();
    }

    /**
     * BIST günlük geçmiş — market-data {@code /api/market/equities/bist/batch-history} (tek HTTP).
     */
    /**
     * Tek BIST sembolü için günlük geçmiş (TRY, adjusted close tercihli).
     */
    public List<MarketPriceHistoryDto> getBistHistoryBetween(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null || symbol.isBlank() || from == null || to == null) {
            return List.of();
        }
        String key = symbol.trim().toUpperCase();
        Map<String, List<MarketPriceHistoryDto>> m = getBistBatchHistoryMapped(key, from, to);
        List<MarketPriceHistoryDto> list = m.get(key);
        return list != null ? list : List.of();
    }

    /**
     * BIST latest satır listesini market-data'dan çeker.
     */
    public List<BistLatestRow> getBistLatestRows() {
        try {
            List<BistLatestRow> rows = marketDataWebClient.get()
                    .uri("/api/market/equities/bist/latest")
                    .retrieve()
                    .bodyToMono(BIST_LATEST_LIST)
                    .timeout(REQUEST_TIMEOUT)
                    .onErrorReturn(List.of())
                    .block(REQUEST_TIMEOUT.plusSeconds(2));
            return rows != null ? rows : List.of();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    /**
     * BIST sembolü için güncel mid TRY fiyatı döner.
     */
    public BigDecimal getBistLatestMidTry(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return BigDecimal.ZERO;
        }
        String want = symbol.trim().toUpperCase();
        try {
            List<BistLatestRow> rows = getBistLatestRows();
            for (BistLatestRow r : rows) {
                if (r == null || r.symbol() == null) {
                    continue;
                }
                if (!want.equals(r.symbol().trim().toUpperCase())) {
                    continue;
                }
                BigDecimal px = r.adjustedClose();
                if (px != null && px.signum() > 0) {
                    return px.setScale(8, RoundingMode.HALF_UP);
                }
                BigDecimal raw = r.rawClose();
                if (raw != null && raw.signum() > 0) {
                    return raw.setScale(8, RoundingMode.HALF_UP);
                }
                return BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        } catch (RuntimeException ignored) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Çoklu BIST sembol history'sini harita olarak döner.
     */
    public Map<String, List<MarketPriceHistoryDto>> getBistBatchHistoryMapped(String symbolsCsv, LocalDate from, LocalDate to) {
        if (symbolsCsv == null || symbolsCsv.isBlank() || from == null || to == null) {
            return Map.of();
        }
        try {
            ApiEnvelope<BistBatchBody> env = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/equities/bist/batch-history")
                            .queryParam("symbols", symbolsCsv)
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiEnvelope<BistBatchBody>>() {})
                    .timeout(HISTORY_REQUEST_TIMEOUT)
                    .onErrorReturn(new ApiEnvelope<>(Boolean.FALSE, null, null, null))
                    .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(10));
            if (env == null || env.data() == null || env.data().historiesBySymbol() == null) {
                return Map.of();
            }
            ZoneId ist = ZoneId.of("Europe/Istanbul");
            Map<String, List<MarketPriceHistoryDto>> out = new HashMap<>();
            for (var e : env.data().historiesBySymbol().entrySet()) {
                String sym = e.getKey() == null ? "" : e.getKey().trim().toUpperCase();
                if (sym.isEmpty()) {
                    continue;
                }
                List<BistHistRow> rows = e.getValue();
                if (rows == null || rows.isEmpty()) {
                    continue;
                }
                List<MarketPriceHistoryDto> converted = rows.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(BistHistRow::date, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(row -> {
                            BigDecimal c;
                            if (row.adjustedClose() != null && row.adjustedClose().signum() > 0) {
                                c = row.adjustedClose();
                            } else if (row.close() != null && row.close().signum() > 0) {
                                c = row.close();
                            } else {
                                return null;
                            }
                            if (row.date() == null) {
                                return null;
                            }
                            LocalDateTime ts = row.date().atStartOfDay(ist).toLocalDateTime();
                            return new MarketPriceHistoryDto(c, c, ts);
                        })
                        .filter(Objects::nonNull)
                        .toList();
                if (!converted.isEmpty()) {
                    out.put(sym, new ArrayList<>(converted));
                }
            }
            return out;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    /**
     * Sayfalanmış BIST latest listesi getirir.
     */
    public BistLatestPage getBistLatestPage(
            int page, int size, String sort, String dir, String filter, String search) {
        try {
            ApiEnvelope<BistLatestPage> envelope = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/equities/bist/latest/page")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .queryParam("sort", sort != null ? sort : "changePercent")
                            .queryParam("dir", dir != null ? dir : "desc")
                            .queryParam("filter", filter != null ? filter : "ALL")
                            .queryParamIfPresent("search", java.util.Optional.ofNullable(search).filter(s -> !s.isBlank()))
                            .build())
                    .retrieve()
                    .bodyToMono(BIST_PAGE_ENVELOPE)
                    .timeout(REQUEST_TIMEOUT.plusSeconds(5))
                    .onErrorReturn(new ApiEnvelope<>(false, null, null, null))
                    .block(REQUEST_TIMEOUT.plusSeconds(6));
            BistLatestPage body = envelope != null ? envelope.data() : null;
            return body != null ? body : emptyBistPage();
        } catch (RuntimeException ignored) {
            return emptyBistPage();
        }
    }

    private static BistLatestPage emptyBistPage() {
        return new BistLatestPage(List.of(), 0, 5, 0, 0, false, false);
    }

    /**
     * VİOP latest kontrat satırlarını listeler.
     */
    public List<ViopLatestRow> getViopLatestRows() {
        return blockListEnvelope("/api/market/viop/latest", VIOP_LATEST_ENVELOPE, REQUEST_TIMEOUT.plusSeconds(10));
    }

    /**
     * Borç/tahvil latest satırlarını listeler.
     */
    public List<DebtLatestRow> getDebtLatestRows() {
        return blockListEnvelope("/api/market/debt/latest", DEBT_LATEST_ENVELOPE, REQUEST_TIMEOUT.plusSeconds(2));
    }

    /**
     * VİOP kontratı için belirli takvim günü fiyatı (market-data price-at).
     */
    public java.util.Optional<ViopPriceAtRow> getViopPriceAt(String contractCode, LocalDate date) {
        if (contractCode == null || contractCode.isBlank() || date == null) {
            return java.util.Optional.empty();
        }
        for (String code : viopContractCodeVariants(contractCode)) {
            java.util.Optional<ViopPriceAtRow> hit = fetchViopPriceAt(code, date);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * {@code price-at} bulamazsa: o günün saatlik history serisinden gün içindeki son geçerli barı döner.
     */
    public java.util.Optional<ViopPriceAtRow> findViopPriceOnCalendarDay(String contractCode, LocalDate date) {
        if (contractCode == null || contractCode.isBlank() || date == null) {
            return java.util.Optional.empty();
        }
        for (String code : viopContractCodeVariants(contractCode)) {
            java.util.Optional<ViopPriceAtRow> hit = fetchViopLastBarOnCalendarDay(code, date);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<ViopPriceAtRow> fetchViopPriceAt(String contractCode, LocalDate date) {
        try {
            ApiEnvelope<ViopPriceAtRow> envelope = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/viop/contracts/{code}/price-at")
                            .queryParam("date", date.toString())
                            .build(contractCode))
                    .retrieve()
                    .bodyToMono(VIOP_PRICE_AT_ENVELOPE)
                    .timeout(HISTORY_REQUEST_TIMEOUT)
                    .onErrorReturn(new ApiEnvelope<>(false, null, null, null))
                    .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(5));
            ViopPriceAtRow row = unwrapEnvelopeData(envelope);
            if (row == null || row.matchType() == null) {
                return java.util.Optional.empty();
            }
            String mt = row.matchType().trim().toUpperCase();
            if ("NOT_FOUND".equals(mt) || row.price() == null || row.price().signum() <= 0) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(row);
        } catch (RuntimeException ignored) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<ViopPriceAtRow> fetchViopLastBarOnCalendarDay(String contractCode, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        try {
            ApiEnvelope<ViopHistoryResponseRow> envelope = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/viop/contracts/{code}/history")
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .queryParam("period", 60)
                            .build(contractCode))
                    .retrieve()
                    .bodyToMono(VIOP_HISTORY_ENVELOPE)
                    .timeout(HISTORY_REQUEST_TIMEOUT)
                    .onErrorReturn(new ApiEnvelope<>(false, null, null, null))
                    .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(5));
            ViopHistoryResponseRow hist = unwrapEnvelopeData(envelope);
            if (hist == null || hist.points() == null || hist.points().isEmpty()) {
                return java.util.Optional.empty();
            }
            return hist.points().stream()
                    .filter(p -> p != null && p.time() != null && p.price() != null && p.price().signum() > 0)
                    .filter(p -> p.time().atZone(VIOP_ZONE).toLocalDate().equals(date))
                    .max(Comparator.comparing(ViopHistoryPointRow::time))
                    .map(p -> new ViopPriceAtRow(
                            contractCode,
                            from,
                            p.time().atZone(VIOP_ZONE).toLocalDateTime(),
                            p.price(),
                            "EXACT",
                            "VIOP_HISTORY",
                            "OK"));
        } catch (RuntimeException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static List<String> viopContractCodeVariants(String contractCode) {
        if (contractCode == null || contractCode.isBlank()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        String u = contractCode.trim().toUpperCase();
        out.add(u);
        String stripped = u.startsWith("F_") ? u.substring(2) : u;
        if (!stripped.isBlank()) {
            out.add(stripped);
            out.add("F_" + stripped);
        }
        return List.copyOf(out);
    }

    private static <T> T unwrapEnvelopeData(ApiEnvelope<T> envelope) {
        if (envelope == null || !Boolean.TRUE.equals(envelope.success())) {
            return null;
        }
        return envelope.data();
    }

    /**
     * ISIN için borç history serisi getirir.
     */
    public List<DebtHistoryRow> getDebtHistory(String isin, int days) {
        if (isin == null || isin.isBlank()) {
            return List.of();
        }
        int safeDays = Math.max(7, Math.min(days, 400));
        try {
            ApiEnvelope<List<DebtHistoryRow>> envelope = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/debt/history")
                            .queryParam("isin", isin.trim().toUpperCase())
                            .queryParam("days", safeDays)
                            .build())
                    .retrieve()
                    .bodyToMono(DEBT_HISTORY_ENVELOPE)
                    .timeout(HISTORY_REQUEST_TIMEOUT)
                    .onErrorReturn(new ApiEnvelope<>(false, null, null, null))
                    .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(5));
            List<DebtHistoryRow> rows = envelope != null ? envelope.data() : null;
            return rows != null ? rows : List.of();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private <T> List<T> blockListEnvelope(
            String uri,
            ParameterizedTypeReference<ApiEnvelope<List<T>>> envelopeType,
            Duration blockTimeout) {
        try {
            ApiEnvelope<List<T>> envelope = marketDataWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(envelopeType)
                    .timeout(REQUEST_TIMEOUT.plusSeconds(8))
                    .onErrorReturn(new ApiEnvelope<>(false, null, null, null))
                    .block(blockTimeout);
            List<T> rows = envelope != null ? envelope.data() : null;
            return rows != null ? rows : List.of();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public record LatestPricingSnapshot(
            Map<String, MarketPriceLatestDto> fx,
            Map<String, MarketPriceLatestDto> metals,
            Map<String, MarketPriceLatestDto> crypto,
            Map<String, MarketPriceLatestDto> funds,
            Map<String, MarketPriceLatestDto> equity
    ) {
        static LatestPricingSnapshot empty() {
            return new LatestPricingSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * TÜFE (CPI) aylık endeks geçmişi — tek HTTP; {@code from}–{@code to} aralığı.
     */
    public CpiIndexLookup loadCpiIndexLookup(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return CpiIndexLookup.empty();
        }
        YearMonth ymFrom = YearMonth.from(from);
        YearMonth ymTo = YearMonth.from(to);
        try {
            ApiEnvelope<InflationHistoryBody> envelope = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/macro/inflation/history")
                            .queryParam("type", "CPI")
                            .queryParam("from", ymFrom.toString())
                            .queryParam("to", ymTo.toString())
                            .build())
                    .retrieve()
                    .bodyToMono(CPI_HISTORY_ENVELOPE)
                    .timeout(HISTORY_REQUEST_TIMEOUT)
                    .onErrorReturn(new ApiEnvelope<>(false, null, null, null))
                    .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(5));
            InflationHistoryBody body = envelope != null ? envelope.data() : null;
            List<InflationHistoryRow> rows = body != null && body.rows() != null ? body.rows() : List.of();
            NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
            for (InflationHistoryRow row : rows) {
                if (row.indexMonth() == null || row.indexValue() == null || row.indexValue().signum() <= 0) {
                    continue;
                }
                LocalDate monthStart = YearMonth.from(row.indexMonth()).atDay(1);
                map.put(monthStart, row.indexValue());
            }
            return new CpiIndexLookup(map);
        } catch (RuntimeException ex) {
            return CpiIndexLookup.empty();
        }
    }

    /**
     * TEFAS fon listesini sayfalı getirir.
     */
    public TefasFundPage getTefasFundPage(int page, int size, String sort, String dir, String search) {
        try {
            ApiEnvelope<TefasFundPage> envelope = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/tefas/funds")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .queryParam("sort", sort != null ? sort : "return1y")
                            .queryParam("dir", dir != null ? dir : "desc")
                            .queryParamIfPresent("search", java.util.Optional.ofNullable(search).filter(s -> !s.isBlank()))
                            .build())
                    .retrieve()
                    .bodyToMono(TEFAS_PAGE_ENVELOPE)
                    .timeout(REQUEST_TIMEOUT.plusSeconds(45))
                    .onErrorReturn(new ApiEnvelope<>(false, null, null, null))
                    .block(REQUEST_TIMEOUT.plusSeconds(50));
            TefasFundPage body =
                    envelope != null && Boolean.TRUE.equals(envelope.success()) ? envelope.data() : null;
            return body != null ? body : emptyTefasPage();
        } catch (RuntimeException ignored) {
            return emptyTefasPage();
        }
    }

    /**
     * TEFAS fon kodu için aylık history getirir.
     */
    public List<TefasHistoryPoint> getTefasFundHistory(String code, int months) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        int safeMonths = Math.max(1, Math.min(months, 60));
        try {
            ApiEnvelope<List<TefasHistoryPoint>> envelope = marketDataWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/market/tefas/funds/{code}/history")
                            .queryParam("months", safeMonths)
                            .build(code.trim().toUpperCase()))
                    .retrieve()
                    .bodyToMono(TEFAS_HISTORY_ENVELOPE)
                    .timeout(HISTORY_REQUEST_TIMEOUT)
                    .onErrorReturn(new ApiEnvelope<>(false, null, null, null))
                    .block(HISTORY_REQUEST_TIMEOUT.plusSeconds(5));
            List<TefasHistoryPoint> list =
                    envelope != null && Boolean.TRUE.equals(envelope.success()) && envelope.data() != null
                            ? envelope.data()
                            : List.of();
            return list;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static TefasFundPage emptyTefasPage() {
        return new TefasFundPage(List.of(), 0, 25, 0, 0, false, false);
    }

    /**
     * Faiz &amp; enflasyon panel özeti — OpenAI context için kısa makro yükü.
     */
    @SuppressWarnings("unchecked")
    /**
 * Makro panel özet verisini market-data'dan yükler.
 */
    public Map<String, Object> loadMacroPanelSummary() {
        try {
            String raw = marketDataWebClient.get()
                    .uri("/api/market/macro/interest-inflation-panel")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(MACRO_PANEL_TIMEOUT)
                    .block(MACRO_PANEL_TIMEOUT.plusSeconds(5));
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
