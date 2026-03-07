package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.EquityProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.finhub.FinHubClient;
import com.nurseli.marketdata.infrastructure.finhub.FinHubQuoteDto;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EquityPriceIngestService {

    private final FinHubClient finHubClient;
    private final MarketPriceHistoryRepository repository;
    private final EquityProperties equityProperties;

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveEquityQuotes() {
        List<String> symbols = equityProperties.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            log.debug("[EQUITY] No symbols configured, skipping");
            return;
        }

        for (String symbol : symbols) {
            try {
                FinHubQuoteDto quote = finHubClient.fetchQuote(symbol).block();
                if (quote == null || quote.getC() == null) {
                    log.warn("[EQUITY] No quote for symbol={}", symbol);
                    continue;
                }

                BigDecimal mid = BigDecimal.valueOf(quote.getC());
                MarketPriceHistory entity = new MarketPriceHistory();
                entity.setSymbol(symbol);
                entity.setBuyPrice(SpreadCalculator.buyPrice(mid));
                entity.setSellPrice(SpreadCalculator.sellPrice(mid));
                entity.setSource("FINHUB");
                LocalDateTime timestamp = quote.getT() != null
                        ? LocalDateTime.ofInstant(Instant.ofEpochSecond(quote.getT()), ZoneId.systemDefault())
                        : LocalDateTime.now();
                entity.setTimestamp(timestamp);

                repository.save(entity);
                log.info("[EQUITY] Saved {} = {}", symbol, mid);
            } catch (Exception e) {
                log.error("[EQUITY] Failed for symbol={}: {}", symbol, e.getMessage());
            }
        }
    }
}