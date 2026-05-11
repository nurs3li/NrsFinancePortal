package com.nurseli.marketdata.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Liquibase yeni kolonları ekledikten sonra veya ilk kurulumda DB rollup alanlarının boş kalmaması için
 * (özellikle {@code viop_seq_move_pct}) tek seferlik yenileme.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViopRollupStartupRunner implements ApplicationRunner {

    private final ViopCsvRollupService viopCsvRollupService;

    @Value("${app.viop.rollups.refresh-on-startup:false}")
    private boolean refreshOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (!refreshOnStartup) {
            return;
        }
        try {
            viopCsvRollupService.refreshAfterCsvImport();
            log.info("[VIOP_ROLLUP] startup refresh completed (app.viop.rollups.refresh-on-startup=true)");
        } catch (Exception ex) {
            log.warn("[VIOP_ROLLUP] startup refresh failed: {}", ex.getMessage());
        }
    }
}
