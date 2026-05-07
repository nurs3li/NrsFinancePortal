package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.FundPriceIngestService;
import com.nurseli.marketdata.config.EtfProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundMarketScheduler {

    private static final int FUND_HISTORY_DAYS = 365;

    private final FundPriceIngestService service;
    private final EtfProperties etfProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void fetchYesterdayOnStartup() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[ETF][STARTUP] Fetching yesterday prices: {}", yesterday);
        List<String> symbols = etfProperties.getSymbols();
        if (symbols != null) {
            symbols.forEach(symbol -> service.ingestForDate(symbol, yesterday));
            symbols.forEach(symbol -> service.ingestHistory(symbol, FUND_HISTORY_DAYS));
        }
    }

    @Scheduled(cron = "0 30 6 * * *", zone = "Europe/Istanbul")
    public void fetchTodayMorning() {
        LocalDate today = LocalDate.now();
        log.info("[ETF][SCHEDULED] Fetching today prices: {}", today);
        List<String> symbols = etfProperties.getSymbols();
        if (symbols != null) {
            symbols.forEach(symbol -> service.ingestForDate(symbol, today));
        }
    }
}