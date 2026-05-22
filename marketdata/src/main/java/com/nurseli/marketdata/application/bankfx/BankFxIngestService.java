package com.nurseli.marketdata.application.bankfx;

import com.nurseli.marketdata.config.BankRatesProperties;
import com.nurseli.marketdata.domain.bankfx.BankFxLatest;
import com.nurseli.marketdata.infrastructure.dovizborsa.DovizborsaBankRatesClient;
import com.nurseli.marketdata.infrastructure.dovizborsa.DovizborsaBankRatesParser;
import com.nurseli.marketdata.infrastructure.dovizborsa.DovizborsaParsedRate;
import com.nurseli.marketdata.repository.BankFxLatestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankFxIngestService {

    private final BankRatesProperties properties;
    private final DovizborsaBankRatesClient client;
    private final DovizborsaBankRatesParser parser;
    private final BankFxLatestRepository repository;

    private final Object scrapeLock = new Object();
    private volatile boolean scrapeRunning;
    private volatile Instant lastScrapeFinishedAt;

    @Transactional
    public int scrapeAndPersist(String trigger) {
        if (!properties.isEnabled()) {
            log.info("[BANK_FX] scrape skipped trigger={} reason=disabled", trigger);
            return 0;
        }
        synchronized (scrapeLock) {
            if (scrapeRunning) {
                log.info("[BANK_FX] scrape skipped trigger={} reason=already_running", trigger);
                return 0;
            }
            long minGapMs = Math.max(60_000L, properties.getMinIntervalBetweenScrapesMs());
            if (lastScrapeFinishedAt != null
                    && Duration.between(lastScrapeFinishedAt, Instant.now()).toMillis() < minGapMs) {
                log.info("[BANK_FX] scrape skipped trigger={} reason=min_interval_ms={}", trigger, minGapMs);
                return 0;
            }
            scrapeRunning = true;
        }
        try {
            String html = client.fetchHtml();
            List<DovizborsaParsedRate> parsed = parser.parse(html);
            if (parsed.isEmpty()) {
                log.warn("[BANK_FX] scrape produced zero rows; keeping previous DB snapshot");
                return 0;
            }
            Instant now = Instant.now();
            int upserts = 0;
            for (DovizborsaParsedRate row : parsed) {
                upserts += upsertRow(row, now);
            }
            log.info("[BANK_FX] scrape ok trigger={} rows={} source={}", trigger, upserts, BankRatesProperties.SOURCE_DOVIZBORSA);
            return upserts;
        } catch (Exception ex) {
            log.warn("[BANK_FX] scrape failed trigger={} reason={}", trigger, ex.getMessage());
            return 0;
        } finally {
            synchronized (scrapeLock) {
                scrapeRunning = false;
                lastScrapeFinishedAt = Instant.now();
            }
        }
    }

    private int upsertRow(DovizborsaParsedRate row, Instant fetchedAt) {
        String source = BankRatesProperties.SOURCE_DOVIZBORSA;
        BankFxLatest entity = repository
                .findBySourceAndBankCodeAndCurrency(source, row.bankCode(), row.currency())
                .orElseGet(BankFxLatest::new);
        entity.setSource(source);
        entity.setBankCode(row.bankCode());
        entity.setBankName(row.bankLabel());
        entity.setCurrency(row.currency());
        entity.setBuyPrice(row.buy());
        entity.setSellPrice(row.sell());
        entity.setChangePct(row.changePct());
        entity.setQuoteTimeText(row.quoteTimeText());
        entity.setFetchedAt(fetchedAt);
        entity.setUpdatedAt(fetchedAt);
        repository.save(entity);
        return 1;
    }
}
