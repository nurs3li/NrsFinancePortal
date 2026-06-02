package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.application.ingest.ViopBackfillRunner;
import com.nurseli.marketdata.config.ViopBackfillProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * VIOP CSV klasörünü periyodik olarak tarar; yeni eklenen {@code viop_YYYYMMDD.csv} dosyalarındaki
 * satırları DB'ye yazar. {@link ViopBackfillRunner#importAllCsvFilesToDb()} idempotent çalıştığı için
 * her tarama çoğunlukla "0 yeni insert" ile sonuçlanır, yalnızca yeni dosya / satır geldiğinde
 * fiili yazma yapılır. Whitelist filtresi sadece okuma (query) tarafında uygulanır; backfill tüm
 * kontratları yazmaya devam eder, böylece ileride whitelist genişletilirse veri geriye dönük olarak
 * hazır olur.
 * <p>
 * Cron süresi {@code app.viop.backfill.incremental.cron} ile dışarıdan ayarlanabilir; default 3
 * saatte bir Europe/Istanbul takvimine göre tetiklenir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViopCsvIncrementalScheduler {

    private final ViopBackfillRunner viopBackfillRunner;
    private final ViopBackfillProperties props;

    @Scheduled(
            cron = "${app.viop.backfill.incremental.cron:0 0 */3 * * *}",
            zone = "${app.viop.backfill.incremental.zone:Europe/Istanbul}"
    )
    public void importNewlyAddedCsvFiles() {
        if (props.getIncremental() == null || !props.getIncremental().isEnabled()) {
            return;
        }
        log.info("[VIOP_BACKFILL] Periyodik tarama basliyor (cron='{}', zone='{}', dir='{}')",
                props.getIncremental().getCron(),
                props.getIncremental().getZone(),
                props.getDir());
        try {
            Map<String, Object> stats = viopBackfillRunner.importAllCsvFilesToDb();
            log.info("[VIOP_BACKFILL] Periyodik tarama tamam: {}", stats);
        } catch (Exception ex) {
            log.warn("[VIOP_BACKFILL] Periyodik tarama hata: {}", ex.getMessage(), ex);
        }
    }
}
