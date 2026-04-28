package com.nurseli.marketdata.application;

import com.nurseli.marketdata.infrastructure.tcmb.TcmbClient;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MarketPriceIngestService {

    private final TcmbClient tcmbClient;
    private final MarketPriceHistoryRepository repository;
    private final TcmbFxSnapshotCache tcmbFxSnapshotCache;

    /**
     * Yeni FX verisi geldiğinde:
     * - DB yazılır
     * - latest-price + indicators + batch cache temizlenir
     */
    @Transactional
    @CacheEvict(
            cacheNames = {"latest-price", "market:batch", "market:indicators"},
            allEntries = true
    )
    public void fetchAndSaveTcmbRates() {
        var rates = tcmbClient.fetchRates();
        LocalDateTime now = LocalDateTime.now();
        tcmbFxSnapshotCache.replaceFromTcmbRates(rates, now);

        rates.forEach(rate -> {

            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(rate.symbol() + "TRY");
            entity.setBuyPrice(rate.buy());
            entity.setSellPrice(rate.sell());
            entity.setSource("TCMB");
            entity.setTimestamp(now);

            repository.save(entity);
        });
    }
}