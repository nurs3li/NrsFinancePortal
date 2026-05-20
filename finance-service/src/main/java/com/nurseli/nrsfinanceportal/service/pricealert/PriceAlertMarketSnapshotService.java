package com.nurseli.nrsfinanceportal.service.pricealert;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertChangeWindow;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PriceAlertMarketSnapshotService {

    private final MarketDataClient marketDataClient;

    public record AssetKey(AssetType assetType, String symbol) {}

    public Map<AssetKey, PriceAlertMarketSnapshot> buildSnapshots(Set<AssetKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }

        MarketDataClient.LatestPricingSnapshot snap = marketDataClient.loadLatestPricing();
        List<MarketDataClient.BistLatestRow> bistRows = loadBistRowsIfNeeded(keys);
        List<MarketDataClient.ViopLatestRow> viopRows = loadViopRowsIfNeeded(keys);
        List<MarketDataClient.DebtLatestRow> debtRows = loadDebtRowsIfNeeded(keys);

        Map<AssetKey, PriceAlertMarketSnapshot> out = new HashMap<>();
        for (AssetKey key : keys) {
            out.put(key, buildOne(key, snap, bistRows, viopRows, debtRows));
        }
        return out;
    }

    private List<MarketDataClient.BistLatestRow> loadBistRowsIfNeeded(Set<AssetKey> keys) {
        boolean needsBist = keys.stream().anyMatch(k -> k.assetType() == AssetType.BIST);
        if (!needsBist) {
            return List.of();
        }
        try {
            List<MarketDataClient.BistLatestRow> rows = marketDataClient.getBistLatestRows();
            return rows != null ? rows : List.of();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<MarketDataClient.ViopLatestRow> loadViopRowsIfNeeded(Set<AssetKey> keys) {
        boolean needs = keys.stream().anyMatch(k -> k.assetType() == AssetType.VIOP);
        if (!needs) {
            return List.of();
        }
        try {
            List<MarketDataClient.ViopLatestRow> rows = marketDataClient.getViopLatestRows();
            return rows != null ? rows : List.of();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<MarketDataClient.DebtLatestRow> loadDebtRowsIfNeeded(Set<AssetKey> keys) {
        boolean needs = keys.stream().anyMatch(k -> k.assetType() == AssetType.BOND);
        if (!needs) {
            return List.of();
        }
        try {
            List<MarketDataClient.DebtLatestRow> rows = marketDataClient.getDebtLatestRows();
            return rows != null ? rows : List.of();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private PriceAlertMarketSnapshot buildOne(
            AssetKey key,
            MarketDataClient.LatestPricingSnapshot snap,
            List<MarketDataClient.BistLatestRow> bistRows,
            List<MarketDataClient.ViopLatestRow> viopRows,
            List<MarketDataClient.DebtLatestRow> debtRows) {

        AssetType type = key.assetType();
        String symbol = key.symbol();

        if (type == AssetType.BIST) {
            return snapshotBist(symbol, bistRows);
        }
        if (type == AssetType.VIOP) {
            return snapshotViop(symbol, viopRows);
        }
        if (type == AssetType.BOND) {
            return snapshotBond(symbol, debtRows);
        }

        BigDecimal price = marketDataClient.getPriceTry(type, symbol, snap);
        if (price == null || price.signum() <= 0) {
            return PriceAlertMarketSnapshot.unavailable();
        }

        BigDecimal changePct = resolveChangePct(type, symbol, bistRows);
        return new PriceAlertMarketSnapshot(price, changePct, true);
    }

    private PriceAlertMarketSnapshot snapshotBist(String symbol, List<MarketDataClient.BistLatestRow> bistRows) {
        String want = symbol.trim().toUpperCase();
        for (MarketDataClient.BistLatestRow row : bistRows) {
            if (row == null || row.symbol() == null) {
                continue;
            }
            if (!want.equals(row.symbol().trim().toUpperCase())) {
                continue;
            }
            BigDecimal px = row.adjustedClose();
            if (px == null || px.signum() <= 0) {
                px = row.rawClose();
            }
            if (px == null || px.signum() <= 0) {
                return PriceAlertMarketSnapshot.unavailable();
            }
            return new PriceAlertMarketSnapshot(px, row.changePercent(), true);
        }
        return PriceAlertMarketSnapshot.unavailable();
    }

    private PriceAlertMarketSnapshot snapshotViop(String symbol, List<MarketDataClient.ViopLatestRow> viopRows) {
        String want = symbol.trim().toUpperCase();
        for (MarketDataClient.ViopLatestRow row : viopRows) {
            if (row == null || row.contractCode() == null) {
                continue;
            }
            if (!want.equals(row.contractCode().trim().toUpperCase())) {
                continue;
            }
            BigDecimal px = row.price();
            if (px == null || px.signum() <= 0) {
                return PriceAlertMarketSnapshot.unavailable();
            }
            return new PriceAlertMarketSnapshot(px, row.listPctChange1d(), true);
        }
        return PriceAlertMarketSnapshot.unavailable();
    }

    private PriceAlertMarketSnapshot snapshotBond(String symbol, List<MarketDataClient.DebtLatestRow> debtRows) {
        String want = symbol.trim().toUpperCase();
        for (MarketDataClient.DebtLatestRow row : debtRows) {
            if (row == null || row.isin() == null) {
                continue;
            }
            if (!want.equals(row.isin().trim().toUpperCase())) {
                continue;
            }
            BigDecimal px = row.dirtyPrice();
            if (px == null || px.signum() <= 0) {
                return PriceAlertMarketSnapshot.unavailable();
            }
            return new PriceAlertMarketSnapshot(px, null, true);
        }
        return PriceAlertMarketSnapshot.unavailable();
    }

    private BigDecimal resolveChangePct(AssetType type, String symbol, List<MarketDataClient.BistLatestRow> ignored) {
        List<MarketPriceHistoryDto> history = marketDataClient.getHistory(type, symbol, 4);
        return dailyChangePercentFromHistory(history);
    }

    /**
     * Son iki günlük kapanıştan günlük % değişim (FX, kripto, metal).
     */
    static BigDecimal dailyChangePercentFromHistory(List<MarketPriceHistoryDto> history) {
        if (history == null || history.size() < 2) {
            return null;
        }
        List<MarketPriceHistoryDto> sorted = history.stream()
                .filter(h -> h != null && h.timestamp() != null)
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .toList();
        if (sorted.size() < 2) {
            return null;
        }
        MarketPriceHistoryDto prev = sorted.get(sorted.size() - 2);
        MarketPriceHistoryDto last = sorted.get(sorted.size() - 1);
        BigDecimal prevPx = closeOf(prev);
        BigDecimal lastPx = closeOf(last);
        if (prevPx == null || lastPx == null || prevPx.signum() == 0) {
            return null;
        }
        return lastPx.subtract(prevPx)
                .divide(prevPx, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal closeOf(MarketPriceHistoryDto row) {
        if (row.buyPrice() != null && row.buyPrice().signum() > 0) {
            return row.buyPrice();
        }
        if (row.sellPrice() != null && row.sellPrice().signum() > 0) {
            return row.sellPrice();
        }
        return null;
    }

    public static PriceAlertChangeWindow defaultWindowFor(AssetType type, boolean isPctCondition) {
        if (!isPctCondition) {
            return null;
        }
        return type == AssetType.CRYPTO ? PriceAlertChangeWindow.HOURS_24 : PriceAlertChangeWindow.DAILY;
    }
}
