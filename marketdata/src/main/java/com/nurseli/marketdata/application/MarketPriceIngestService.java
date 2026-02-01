package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.tcmb.TcmbClient;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MarketPriceIngestService {

    private final TcmbClient tcmbClient;
    private final MarketPriceHistoryRepository repository;

    @Transactional
    public void fetchAndSaveTcmbRates() {
        tcmbClient.fetchRates().forEach(rate -> {

            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(rate.symbol() + "TRY");
            entity.setBuyPrice(rate.buy());
            entity.setSellPrice(rate.sell());
            entity.setSource("TCMB");
            entity.setTimestamp(LocalDateTime.now());

            repository.save(entity);
        });
    }
}