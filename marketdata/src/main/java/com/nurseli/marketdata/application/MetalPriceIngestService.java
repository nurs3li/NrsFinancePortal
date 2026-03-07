package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.coingecko.CoinGeckoMetalClient;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetalPriceIngestService {

    private static final BigDecimal OUNCE_TO_GRAM =
            new BigDecimal("31.1034768");

    private final CoinGeckoMetalClient client;
    private final MarketPriceHistoryRepository repository;

    @Transactional
    @CacheEvict(
            cacheNames = {"market:batch", "market:indicators"},
            allEntries = true
    )
    public void fetchAndSaveGramGold() {

        BigDecimal ouncePriceTry = client.fetchGoldTryPerOunce();
        if (ouncePriceTry == null) {
            log.warn("[METAL] No price from CoinGecko (rate limit or error), skipping update.");
            return;
        }

        BigDecimal gramPrice =
                ouncePriceTry.divide(
                        OUNCE_TO_GRAM,
                        2,
                        RoundingMode.HALF_UP
                );

        MarketPriceHistory entity = new MarketPriceHistory();
        entity.setSymbol("XAU_TRY");
        entity.setBuyPrice(SpreadCalculator.buyPrice(gramPrice));
        entity.setSellPrice(SpreadCalculator.sellPrice(gramPrice));
        entity.setSource("COINGECKO");
        entity.setTimestamp(LocalDateTime.now());

        repository.save(entity);

        log.info("[METAL] Saved GRAM GOLD = {} TRY", gramPrice);
    }
}