package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.NewsIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsFetchScheduler {

    private final NewsIngestService newsIngestService;

    /**
     * Haberleri periyodik olarak çeker (1 saatte bir)
     * FAZ 3.1.4
     */
    @Scheduled(fixedDelay = 3600_000) // 1 saat = 3600000 ms
    public void fetchNews() {
        log.info("[SCHEDULER] Fetching news from FinHub");
        newsIngestService.fetchAndSaveNews();
    }
}