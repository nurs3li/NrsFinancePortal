package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.price.CryptoSymbolMapping;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;

import com.nurseli.marketdata.infrastructure.coingecko.CoinGeckoClient;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoPriceIngestService {

    private final CoinGeckoClient coinGeckoClient;
    private final MarketPriceHistoryRepository repository;

    @Transactional
    public void fetchAndSaveCryptoPrices() {

        String ids = CryptoSymbolMapping.idsAsCsv();

        Map<String, Map<String, Double>> response =
                coinGeckoClient.fetchPrices(ids);

        if (response.isEmpty()) {
            log.warn("[CRYPTO] No data received from CoinGecko");
            return;
        }

        response.forEach((coinId, priceMap) -> {

            Number usdNumber = priceMap.get("usd");
            if (usdNumber == null) return;

            BigDecimal usdPrice = BigDecimal.valueOf(usdNumber.doubleValue());

            String symbol = CryptoSymbolMapping.SYMBOL_TO_ID.entrySet()
                    .stream()
                    .filter(e -> e.getValue().equals(coinId))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);

            if (symbol == null) return;

            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(symbol);
            entity.setBuyPrice(usdPrice);
            entity.setSellPrice(usdPrice);

            entity.setBuyPrice(usdPrice);
            entity.setSellPrice(usdPrice);
            entity.setSource("COINGECKO");
            entity.setTimestamp(LocalDateTime.now());

            repository.save(entity);
            log.info("[CRYPTO] Saved {}", symbol);
        });
    }
}