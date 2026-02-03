package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.coingecko.CoinGeckoMetalClient;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public void fetchAndSaveGramGold() {

        BigDecimal ouncePriceTry =
                client.fetchGoldTryPerOunce();

        BigDecimal gramPrice =
                ouncePriceTry.divide(
                        OUNCE_TO_GRAM,
                        2,
                        RoundingMode.HALF_UP
                );

        MarketPriceHistory entity = new MarketPriceHistory();
        entity.setSymbol("XAU_TRY");
        entity.setBuyPrice(gramPrice);
        entity.setSellPrice(gramPrice);
        entity.setSource("COINGECKO");
        entity.setTimestamp(LocalDateTime.now());

        repository.save(entity);

        log.info("[METAL] Saved GRAM GOLD = {} TRY", gramPrice);
    }
}