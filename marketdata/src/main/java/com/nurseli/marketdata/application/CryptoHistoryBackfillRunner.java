package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.CryptoHistoryBackfillProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CryptoHistoryBackfillRunner implements ApplicationRunner {
    private final CryptoHistoryBackfillProperties properties;
    private final CryptoHistoryWarmupService warmupService;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        boolean enabled = properties.isEnabled()
                || envBoolean("CRYPTO_HISTORY_BACKFILL_ENABLED")
                || envBoolean("APP_CRYPTO_HISTORY_BACKFILL_ENABLED");
        int periodDays = resolveInt(properties.getPeriodDays(),
                "CRYPTO_HISTORY_BACKFILL_PERIOD_DAYS", "APP_CRYPTO_HISTORY_BACKFILL_PERIOD_DAYS", 365);
        boolean shutdownOnComplete = properties.isShutdownOnComplete()
                || envBoolean("CRYPTO_HISTORY_BACKFILL_SHUTDOWN_ON_COMPLETE")
                || envBoolean("APP_CRYPTO_HISTORY_BACKFILL_SHUTDOWN_ON_COMPLETE");

        log.info("[CRYPTO_HISTORY_BACKFILL] Config resolved enabled={} periodDays={} shutdownOnComplete={}",
                enabled, periodDays, shutdownOnComplete);
        if (!enabled) {
            return;
        }
        try {
            warmupService.warmSupportedSymbolsSync(periodDays, "startup-backfill");
            log.info("[CRYPTO_HISTORY_BACKFILL] Done");
        } catch (Exception ex) {
            log.error("[CRYPTO_HISTORY_BACKFILL] Failed: {}", ex.getMessage(), ex);
        } finally {
            if (shutdownOnComplete) {
                log.info("[CRYPTO_HISTORY_BACKFILL] shutdownOnComplete=true, shutting down application");
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
        if (fromEnv != null) return fromEnv;
        Integer fromAppEnv = envInt(appEnvKey);
        if (fromAppEnv != null) return fromAppEnv;
        return configured > 0 ? configured : defaultValue;
    }

    private Integer envInt(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return null;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
