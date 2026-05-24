package com.nurseli.marketdata.infrastructure.evds;

import com.nurseli.marketdata.config.MarketBootstrapProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * EVDS gibi sınırlı API'lerde ardışık istekler arası minimum bekleme.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EvdsRequestThrottle {

    private final MarketBootstrapProperties bootstrapProperties;
    private long lastRequestAtMs;

    public void awaitTurn(String label) {
        long minMs = Math.max(0L, bootstrapProperties.getEvdsMinIntervalMs());
        if (minMs <= 0) {
            return;
        }
        synchronized (this) {
            long now = System.currentTimeMillis();
            long wait = minMs - (now - lastRequestAtMs);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.warn("[EVDS_THROTTLE] interrupted label={}", label);
                }
            }
            lastRequestAtMs = System.currentTimeMillis();
        }
    }
}
