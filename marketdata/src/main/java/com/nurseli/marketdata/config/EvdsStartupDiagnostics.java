package com.nurseli.marketdata.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EvdsStartupDiagnostics {

    private final EvdsProperties evdsProperties;

    @PostConstruct
    void logEvdsConfig() {
        if (!evdsProperties.isEnabled()) {
            log.warn("[EVDS] disabled — macro inflation/policy series will use DB fallback only");
            return;
        }
        String key = evdsProperties.getApiKey();
        if (key == null || key.isBlank()) {
            log.error(
                    "[EVDS] EVDS_API_KEY is empty — live TCMB fetch will fail; set EVDS_API_KEY in .env or environment");
        }
        log.info("[EVDS] baseUrl={} timeoutMs={}", evdsProperties.getBaseUrl(), evdsProperties.getTimeoutMs());
    }
}
