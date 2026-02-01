package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.MarketPriceIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@EnableScheduling
@Component
@RequiredArgsConstructor
public class MarketDataScheduler {

    private final MarketPriceIngestService service;

    @Scheduled(fixedRate = 300_000) // 5 dakika
    public void fetchTcmbRates() {
        service.fetchAndSaveTcmbRates();
    }
}