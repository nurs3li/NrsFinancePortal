package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.application.portfolio.HistoricalManualPriceResolverService.ManualChartPoint;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualSymbolDailyCloseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ManualSymbolDailyCloseStore {

    private final ManualSymbolDailyCloseRepository repository;

    @Transactional
    public void upsertSeries(Long userId, AssetType assetType, String symbol, List<ManualChartPoint> points) {
        if (userId == null || assetType == null || symbol == null || symbol.isBlank() || points == null || points.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        String normalizedSymbol = symbol.trim();
        for (ManualChartPoint pt : points) {
            if (pt == null || pt.date() == null || pt.priceTry() == null || pt.priceTry().signum() <= 0) {
                continue;
            }
            repository.upsertNative(
                    userId,
                    assetType.name(),
                    normalizedSymbol,
                    pt.date(),
                    pt.priceTry(),
                    "MARKET_HISTORY",
                    now
            );
        }
    }

    @Transactional(readOnly = true)
    public NavigableMap<LocalDate, BigDecimal> loadRange(
            Long userId,
            AssetType assetType,
            String symbol,
            LocalDate from,
            LocalDate to
    ) {
        TreeMap<LocalDate, BigDecimal> tree = new TreeMap<>();
        if (userId == null || assetType == null || symbol == null || symbol.isBlank() || from == null || to == null) {
            return tree;
        }
        repository.findRange(userId, assetType, symbol.trim(), from, to).forEach(row -> {
            if (row.getCloseTry() != null && row.getCloseTry().signum() > 0) {
                tree.put(row.getTradeDate(), row.getCloseTry());
            }
        });
        return tree;
    }

    @Transactional
    public void deleteForUser(Long userId) {
        if (userId == null) {
            return;
        }
        repository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> findLatestDate(Long userId, AssetType assetType, String symbol) {
        if (userId == null || assetType == null || symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return repository.findLatestTradeDate(userId, assetType, symbol.trim());
    }
}
