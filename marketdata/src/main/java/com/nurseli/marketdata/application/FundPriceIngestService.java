package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.finhub.FinHubClient;
import com.nurseli.marketdata.infrastructure.finhub.FinHubQuoteDto;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundPriceIngestService {

    private static final String SOURCE_ETF = "ETF";

    private final FinHubClient finHubClient;
    private final MarketPriceHistoryRepository repository;

    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void ingestForDate(String symbol, LocalDate date) {
        try {
            FinHubQuoteDto quote = finHubClient.fetchQuote(symbol).block();
            if (quote == null || quote.getC() == null) {
                log.warn("[ETF] No quote for symbol={} referenceDate={}", symbol, date);
                return;
            }

            // Bugün için current (c), dün için previous close (pc)
            double priceValue = date.equals(LocalDate.now()) ? quote.getC() : (quote.getPc() != null ? quote.getPc() : quote.getC());
            BigDecimal mid = BigDecimal.valueOf(priceValue);

            boolean exists = repository
                    .findTopBySymbolOrderByTimestampDesc(symbol)
                    .map(e -> e.getTimestamp().toLocalDate().equals(date))
                    .orElse(false);

            if (exists) {
                log.info("[ETF] Already exists symbol={} date={}", symbol, date);
                return;
            }

            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(symbol);
            entity.setBuyPrice(SpreadCalculator.buyPrice(mid));
            entity.setSellPrice(SpreadCalculator.sellPrice(mid));
            entity.setSource(SOURCE_ETF);
            entity.setTimestamp(LocalDateTime.of(date, java.time.LocalTime.NOON));

            repository.save(entity);
            log.info("[ETF] SAVED symbol={} price={} date={}", symbol, mid, date);

        } catch (Exception e) {
            log.error("[ETF] INGEST FAILED symbol={} date={}", symbol, date, e);
        }
    }
}