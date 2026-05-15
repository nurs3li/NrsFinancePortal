package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.EquityHistoryBackfillProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EquityHistoryBackfillRunner implements ApplicationRunner {
    private final EquityHistoryBackfillProperties properties;
    private final EquityPriceIngestService ingestService;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        boolean enabled = properties.isEnabled() || envBoolean("EQUITY_HISTORY_BACKFILL_ENABLED")
                || envBoolean("APP_EQUITY_HISTORY_BACKFILL_ENABLED");
        int periodDays = resolveInt(properties.getPeriodDays(),
                "EQUITY_HISTORY_BACKFILL_PERIOD_DAYS", "APP_EQUITY_HISTORY_BACKFILL_PERIOD_DAYS", 730);
        int batchSize = resolveInt(properties.getBatchSize(),
                "EQUITY_HISTORY_BACKFILL_BATCH_SIZE", "APP_EQUITY_HISTORY_BACKFILL_BATCH_SIZE", 20);
        boolean shutdownOnComplete = properties.isShutdownOnComplete()
                || envBoolean("EQUITY_HISTORY_BACKFILL_SHUTDOWN_ON_COMPLETE")
                || envBoolean("APP_EQUITY_HISTORY_BACKFILL_SHUTDOWN_ON_COMPLETE");

        log.info("[EQUITY_HISTORY_BACKFILL] Config resolved enabled={} periodDays={} batchSize={} shutdownOnComplete={}",
                enabled, periodDays, batchSize, shutdownOnComplete);

        if (!enabled) {
            return;
        }
        log.info("[EQUITY_HISTORY_BACKFILL] Starting one-shot backfill periodDays={} batchSize={}", periodDays, batchSize);
        try {
            ingestService.fetchAndSaveHistoryBackfill(periodDays, batchSize);
            log.info("[EQUITY_HISTORY_BACKFILL] Done");
        } catch (Exception ex) {
            log.error("[EQUITY_HISTORY_BACKFILL] Failed: {}", ex.getMessage(), ex);
        } finally {
            if (shutdownOnComplete) {
                log.info("[EQUITY_HISTORY_BACKFILL] shutdownOnComplete=true, shutting down application");
                System.exit(org.springframework.boot.SpringApplication.exit(context, () -> 0));
            }
        }
    }

    private boolean envBoolean(String key) {
        String value = System.getenv(key);
        return value != null && Boolean.parseBoolean(value.trim());
    }

    private int resolveInt(int configured, String envKey, String appEnvKey, int defaultValue) {
        Integer fromEnv = envInt(envKey);
        if (fromEnv != null) {
            return fromEnv;
        }
        Integer fromAppEnv = envInt(appEnvKey);
        if (fromAppEnv != null) {
            return fromAppEnv;
        }
        return configured > 0 ? configured : defaultValue;
    }

    private Integer envInt(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
