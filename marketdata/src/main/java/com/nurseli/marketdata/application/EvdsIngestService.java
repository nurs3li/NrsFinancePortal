package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.evds.EvdsClient;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvdsIngestService {

    private final EvdsClient evdsClient;
    private final MarketPriceHistoryRepository repository;

    @Transactional
    public void fetchAndSaveRecentFxHistory() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(7);
        for (String symbol : List.of("USDTRY", "EURTRY")) {
            evdsClient.getHistoricalFx(symbol, start, end).forEach(p -> {
                MarketPriceHistory e = new MarketPriceHistory();
                e.setSymbol(p.symbol());
                e.setBuyPrice(p.price());
                e.setSellPrice(p.price());
                e.setSource("EVDS");
                e.setTimestamp(p.timestamp());
                repository.save(e);
            });
        }
    }
}
