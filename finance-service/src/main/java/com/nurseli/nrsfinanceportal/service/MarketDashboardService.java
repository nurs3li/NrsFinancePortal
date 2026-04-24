package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.dto.*;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketDashboardService {

    private static final int HISTORY_DAYS = 14;
    private static final int SPARKLINE_POINTS = 24;
    private static final String MODE_EQUITY_FINVIZ = "EQUITY_FINVIZ";
    private static final String MODE_MULTI_ASSET = "MULTI_ASSET";
    private static final String HORIZON_1D = "1D";
    private static final String HORIZON_14D = "14D";
    private static final String WEIGHT_MARKET_CAP = "MARKET_CAP";
    private static final String WEIGHT_EQUAL = "EQUAL";
    private static final String WEIGHT_PRICE_SQRT = "PRICE_SQRT";

    /** Finviz benzeri sektör etiketleri (US hisse evreni). */
    private static final Map<String, String> EQUITY_SECTOR = Map.ofEntries(
            Map.entry("AAPL", "TECHNOLOGY"),
            Map.entry("MSFT", "TECHNOLOGY"),
            Map.entry("GOOGL", "COMMUNICATION SERVICES"),
            Map.entry("AMZN", "CONSUMER CYCLICAL"),
            Map.entry("META", "COMMUNICATION SERVICES"),
            Map.entry("NVDA", "TECHNOLOGY"),
            Map.entry("TSLA", "CONSUMER CYCLICAL"),
            Map.entry("JPM", "FINANCIAL"),
            Map.entry("JNJ", "HEALTHCARE"),
            Map.entry("V", "FINANCIAL"),
            Map.entry("WMT", "CONSUMER DEFENSIVE")
    );
    private static final Map<String, String> EQUITY_INDUSTRY = Map.ofEntries(
            Map.entry("AAPL", "CONSUMER ELECTRONICS"),
            Map.entry("MSFT", "SOFTWARE - INFRASTRUCTURE"),
            Map.entry("GOOGL", "INTERNET CONTENT & INFORMATION"),
            Map.entry("AMZN", "INTERNET RETAIL"),
            Map.entry("META", "INTERNET CONTENT & INFORMATION"),
            Map.entry("NVDA", "SEMICONDUCTORS"),
            Map.entry("TSLA", "AUTO MANUFACTURERS"),
            Map.entry("JPM", "BANKS - DIVERSIFIED"),
            Map.entry("JNJ", "DRUG MANUFACTURERS - GENERAL"),
            Map.entry("V", "CREDIT SERVICES"),
            Map.entry("WMT", "DISCOUNT STORES")
    );

    private final MarketOverviewService overviewService;
    private final MarketDataClient marketDataClient;

    public MarketDashboardResponse buildDashboard() {
        MarketOverviewResponse latest = overviewService.getOverview();

        List<SparklineEntry> sparklines = new ArrayList<>();
        List<VolatilityEntry> volatility = new ArrayList<>();
        List<HeatmapTileEntry> heatmapTiles = new ArrayList<>();

        processCategory(latest, AssetType.FX, "FX", latest.doviz().keySet(), sparklines, volatility, heatmapTiles);
        processCategory(latest, AssetType.CRYPTO, "CRYPTO", latest.crypto().keySet(), sparklines, volatility, heatmapTiles);
        processCategory(latest, AssetType.METAL, "METAL", latest.metals().keySet(), sparklines, volatility, heatmapTiles);
        processCategory(latest, AssetType.FUND, "FUND", latest.funds().keySet(), sparklines, volatility, heatmapTiles);
        addEquityTiles(latest, sparklines, volatility, heatmapTiles);

        return new MarketDashboardResponse(
                latest,
                sparklines,
                heatmapTiles,
                new HeatmapMeta(
                        MODE_EQUITY_FINVIZ,
                        HORIZON_1D,
                        WEIGHT_MARKET_CAP + "->" + WEIGHT_EQUAL + " (fallback)",
                        MODE_MULTI_ASSET,
                        HORIZON_14D,
                        WEIGHT_PRICE_SQRT
                ),
                volatility,
                java.time.LocalDateTime.now()
        );
    }

    private void addEquityTiles(
            MarketOverviewResponse latest,
            List<SparklineEntry> sparklines,
            List<VolatilityEntry> volatility,
            List<HeatmapTileEntry> heatmapTiles
    ) {
        for (var e : latest.stocks().entrySet()) {
            String symbol = e.getKey();
            StockOverviewDto dto = e.getValue();
            BigDecimal marketCap = dto != null ? dto.marketCap() : null;
            boolean hasMarketCap = marketCap != null && marketCap.signum() > 0;
            double weight = hasMarketCap ? marketCap.doubleValue() : 1d;
            String equityWeightMode = hasMarketCap ? WEIGHT_MARKET_CAP : WEIGHT_EQUAL;
            String sector = EQUITY_SECTOR.getOrDefault(symbol, "EQUITY");
            String industry = EQUITY_INDUSTRY.getOrDefault(symbol, "OTHER");
            List<MarketPriceHistoryDto> raw = fetchHistory(AssetType.STOCK, symbol);
            List<BigDecimal> closes = midClosesSorted(raw);

            if (closes.size() >= 2) {
                sparklines.add(new SparklineEntry("STOCK", symbol, downsample(closes, SPARKLINE_POINTS)));
                volatility.add(new VolatilityEntry("STOCK", symbol, round4(dailyReturnStdDev(closes))));
                double pct = pctChange1D(raw);
                heatmapTiles.add(new HeatmapTileEntry(
                        sector,
                        industry,
                        symbol,
                        "STOCK",
                        round4(pct),
                        weight,
                        MODE_EQUITY_FINVIZ,
                        HORIZON_1D,
                        equityWeightMode,
                        dto != null ? dto.marketCapSource() : null,
                        dto != null ? dto.marketCapAsOf() : null
                ));
            } else if (closes.size() == 1) {
                sparklines.add(new SparklineEntry("STOCK", symbol, new ArrayList<>(closes)));
                volatility.add(new VolatilityEntry("STOCK", symbol, 0));
                heatmapTiles.add(new HeatmapTileEntry(
                        sector,
                        industry,
                        symbol,
                        "STOCK",
                        0,
                        weight,
                        MODE_EQUITY_FINVIZ,
                        HORIZON_1D,
                        equityWeightMode,
                        dto != null ? dto.marketCapSource() : null,
                        dto != null ? dto.marketCapAsOf() : null
                ));
            } else {
                heatmapTiles.add(new HeatmapTileEntry(
                        sector,
                        industry,
                        symbol,
                        "STOCK",
                        0,
                        weight,
                        MODE_EQUITY_FINVIZ,
                        HORIZON_1D,
                        equityWeightMode,
                        dto != null ? dto.marketCapSource() : null,
                        dto != null ? dto.marketCapAsOf() : null
                ));
            }
        }
    }

    private void processCategory(
            MarketOverviewResponse latest,
            AssetType type,
            String assetClass,
            Set<String> symbols,
            List<SparklineEntry> sparklines,
            List<VolatilityEntry> volatility,
            List<HeatmapTileEntry> heatmapTiles
    ) {
        for (String symbol : symbols) {
            List<MarketPriceHistoryDto> raw = fetchHistory(type, symbol);
            List<BigDecimal> closes = midClosesSorted(raw);
            BigDecimal px = latestPrice(latest, type, symbol);
            double weight = layoutWeight(px);
            String sector = sectorFor(assetClass, symbol);

            if (closes.size() >= 2) {
                sparklines.add(new SparklineEntry(assetClass, symbol, downsample(closes, SPARKLINE_POINTS)));
                double vol = dailyReturnStdDev(closes);
                volatility.add(new VolatilityEntry(assetClass, symbol, round4(vol)));
                double pct = pctChangeHeatmapWindow(closes);
                heatmapTiles.add(new HeatmapTileEntry(
                        sector,
                        null,
                        symbol,
                        assetClass,
                        round4(pct),
                        weight,
                        MODE_MULTI_ASSET,
                        HORIZON_14D,
                        WEIGHT_PRICE_SQRT,
                        null,
                        null
                ));
            } else if (closes.size() == 1) {
                sparklines.add(new SparklineEntry(assetClass, symbol, new ArrayList<>(closes)));
                volatility.add(new VolatilityEntry(assetClass, symbol, 0));
                heatmapTiles.add(new HeatmapTileEntry(
                        sector,
                        null,
                        symbol,
                        assetClass,
                        0,
                        weight,
                        MODE_MULTI_ASSET,
                        HORIZON_14D,
                        WEIGHT_PRICE_SQRT,
                        null,
                        null
                ));
            } else {
                heatmapTiles.add(new HeatmapTileEntry(
                        sector,
                        null,
                        symbol,
                        assetClass,
                        0,
                        weight,
                        MODE_MULTI_ASSET,
                        HORIZON_14D,
                        WEIGHT_PRICE_SQRT,
                        null,
                        null
                ));
            }
        }
    }

    private String sectorFor(String assetClass, String symbol) {
        return switch (assetClass) {
            case "FX" -> "FOREX";
            case "CRYPTO" -> "CRYPTO";
            case "METAL" -> "COMMODITIES";
            case "FUND" -> "ETFS & INDEX";
            default -> "OTHER";
        };
    }

    private BigDecimal latestPrice(MarketOverviewResponse latest, AssetType type, String symbol) {
        return switch (type) {
            case FX -> {
                FxOverviewDto d = latest.doviz().get(symbol);
                if (d == null) yield BigDecimal.ONE;
                if (d.buy() != null && d.buy().signum() > 0) yield d.buy();
                yield d.sell() != null ? d.sell() : BigDecimal.ONE;
            }
            case CRYPTO -> {
                CryptoOverviewDto d = latest.crypto().get(symbol);
                yield d != null && d.buyPrice() != null && d.buyPrice().signum() > 0 ? d.buyPrice() : BigDecimal.ONE;
            }
            case METAL -> {
                MetalOverviewDto d = latest.metals().get(symbol);
                yield d != null && d.buyPrice() != null && d.buyPrice().signum() > 0 ? d.buyPrice() : BigDecimal.ONE;
            }
            case FUND -> {
                FundOverviewDto d = latest.funds().get(symbol);
                yield d != null && d.buyPrice() != null && d.buyPrice().signum() > 0 ? d.buyPrice() : BigDecimal.ONE;
            }
            case STOCK -> {
                StockOverviewDto d = latest.stocks().get(symbol);
                yield d != null && d.buyPrice() != null && d.buyPrice().signum() > 0 ? d.buyPrice() : BigDecimal.ONE;
            }
            default -> BigDecimal.ONE;
        };
    }

    private static double layoutWeight(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            return 1;
        }
        double p = price.doubleValue();
        return Math.sqrt(Math.max(p, 1e-6));
    }

    private List<MarketPriceHistoryDto> fetchHistory(AssetType type, String symbol) {
        try {
            List<MarketPriceHistoryDto> list = marketDataClient.getHistory(type, symbol, HISTORY_DAYS);
            return list != null ? list : List.of();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<BigDecimal> midClosesSorted(List<MarketPriceHistoryDto> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .map(MarketDashboardService::midPrice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BigDecimal midPrice(MarketPriceHistoryDto d) {
        if (d.buyPrice() != null && d.sellPrice() != null) {
            return d.buyPrice().add(d.sellPrice()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        }
        if (d.buyPrice() != null) {
            return d.buyPrice();
        }
        return d.sellPrice();
    }

    private static List<BigDecimal> downsample(List<BigDecimal> series, int target) {
        if (series.isEmpty()) {
            return List.of();
        }
        if (series.size() <= target) {
            return new ArrayList<>(series);
        }
        List<BigDecimal> out = new ArrayList<>(target);
        int n = series.size();
        for (int i = 0; i < target; i++) {
            int idx = n == 1 ? 0 : (int) Math.round(i * (n - 1) / (double) (target - 1));
            out.add(series.get(idx));
        }
        return out;
    }

    private static double dailyReturnStdDev(List<BigDecimal> closes) {
        if (closes.size() < 3) {
            return 0;
        }
        List<Double> rets = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            BigDecimal p0 = closes.get(i - 1);
            BigDecimal p1 = closes.get(i);
            if (p0.signum() == 0) {
                continue;
            }
            double r = p1.subtract(p0).divide(p0, 12, RoundingMode.HALF_UP).doubleValue();
            rets.add(r);
        }
        if (rets.size() < 2) {
            return 0;
        }
        double mean = rets.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = rets.stream().mapToDouble(r -> (r - mean) * (r - mean)).average().orElse(0);
        return Math.sqrt(var);
    }

    /**
     * Isı haritası rengi için % değişim: serinin ilk ve son orta fiyatı (pencere ≈ {@link #HISTORY_DAYS} gün).
     * Dakika kovası veride "son iki nokta" ardışık olunca ~0% çıkmasın diye ardışık değil, uçtan uca kullanılır.
     */
    private static double pctChangeHeatmapWindow(List<BigDecimal> closes) {
        if (closes.size() < 2) {
            return 0;
        }
        BigDecimal first = closes.getFirst();
        BigDecimal last = closes.getLast();
        if (first == null || last == null || first.signum() == 0) {
            return 0;
        }
        return last.subtract(first).divide(first, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    /**
     * Equity Finviz benzeri renk mantığı: son 24 saat içindeki ilk orta fiyat -> en son orta fiyat.
     * Veri seyrekse güvenli fallback olarak serinin son iki noktasını kullanır.
     */
    private static double pctChange1D(List<MarketPriceHistoryDto> raw) {
        if (raw == null || raw.size() < 2) {
            return 0;
        }
        List<MarketPriceHistoryDto> sorted = raw.stream()
                .filter(Objects::nonNull)
                .filter(d -> d.timestamp() != null)
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .toList();
        if (sorted.size() < 2) {
            return 0;
        }

        MarketPriceHistoryDto lastRow = sorted.getLast();
        BigDecimal lastMid = midPrice(lastRow);
        if (lastMid == null || lastMid.signum() == 0 || lastRow.timestamp() == null) {
            return 0;
        }

        java.time.LocalDateTime threshold = lastRow.timestamp().minusDays(1);
        BigDecimal baseMid = null;
        for (MarketPriceHistoryDto row : sorted) {
            if (row.timestamp().isBefore(threshold)) {
                continue;
            }
            baseMid = midPrice(row);
            if (baseMid != null) {
                break;
            }
        }
        if (baseMid == null || baseMid.signum() == 0) {
            baseMid = midPrice(sorted.get(sorted.size() - 2));
        }
        if (baseMid == null || baseMid.signum() == 0) {
            return 0;
        }
        return lastMid.subtract(baseMid).divide(baseMid, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private static double round4(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }
}
