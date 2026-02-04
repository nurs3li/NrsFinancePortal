package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.FundPriceIngestService;
import com.nurseli.marketdata.infrastructure.tefas.TefasProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundMarketScheduler {

    private final FundPriceIngestService service;
    private final TefasProperties properties;

    // 🔥 UYGULAMA AÇILINCA → DÜNÜ ÇEK
    @EventListener(ApplicationReadyEvent.class)
    public void fetchYesterdayOnStartup() {

        LocalDate yesterday = LocalDate.now().minusDays(1);

        log.info("[TEFAS][STARTUP] Fetching yesterday prices: {}", yesterday);

        properties.getFunds()
                .forEach(fund ->
                        service.ingestForDate(fund, yesterday)
                );
    }

    // ⏰ HER GÜN 06:30 TR → BUGÜNÜ GÜNCELLE
    @Scheduled(cron = "0 30 6 * * *", zone = "Europe/Istanbul")
    public void fetchTodayMorning() {

        LocalDate today = LocalDate.now();

        log.info("[TEFAS][SCHEDULED] Fetching today prices: {}", today);

        properties.getFunds()
                .forEach(fund ->
                        service.ingestForDate(fund, today)
                );
    }
}