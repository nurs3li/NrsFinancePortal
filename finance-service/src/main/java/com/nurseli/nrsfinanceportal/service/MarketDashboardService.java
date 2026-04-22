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
            BigDecimal px = dto != null ? dto.buyPrice() : null;
            double weight = layoutWeight(px);
            String sector = EQUITY_SECTOR.getOrDefault(symbol, "EQUITY");
            List<MarketPriceHistoryDto> raw = fetchHistory(AssetType.STOCK, symbol);
            List<BigDecimal> closes = midClosesSorted(raw);

            if (closes.size() >= 2) {
                sparklines.add(new SparklineEntry("STOCK", symbol, downsample(closes, SPARKLINE_POINTS)));
                volatility.add(new VolatilityEntry("STOCK", symbol, round4(dailyReturnStdDev(closes))));
                double pct = pctChangeHeatmapWindow(closes);
                heatmapTiles.add(new HeatmapTileEntry(sector, symbol, "STOCK", round4(pct), weight));
            } else if (closes.size() == 1) {
                sparklines.add(new SparklineEntry("STOCK", symbol, new ArrayList<>(closes)));
                volatility.add(new VolatilityEntry("STOCK", symbol, 0));
                heatmapTiles.add(new HeatmapTileEntry(sector, symbol, "STOCK", 0, weight));
            } else {
                heatmapTiles.add(new HeatmapTileEntry(sector, symbol, "STOCK", 0, weight));
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
                heatmapTiles.add(new HeatmapTileEntry(sector, symbol, assetClass, round4(pct), weight));
            } else if (closes.size() == 1) {
                sparklines.add(new SparklineEntry(assetClass, symbol, new ArrayList<>(closes)));
                volatility.add(new VolatilityEntry(assetClass, symbol, 0));
                heatmapTiles.add(new HeatmapTileEntry(sector, symbol, assetClass, 0, weight));
            } else {
                heatmapTiles.add(new HeatmapTileEntry(sector, symbol, assetClass, 0, weight));
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

    private static double round4(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }
}
