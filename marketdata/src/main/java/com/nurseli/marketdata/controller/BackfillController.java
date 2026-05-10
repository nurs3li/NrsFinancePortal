package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.application.MarketPriceBackfillService;
import com.nurseli.marketdata.application.ViopBackfillRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Sadece internal / admin amaçlı backfill endpoint'i.
 * İstersen reverse proxy / network policy ile dış dünyaya kapatabilirsin.
 */
@RestController
@RequestMapping("/internal/market/backfill")
@RequiredArgsConstructor
@Deprecated(forRemoval = false, since = "2026-04")
public class BackfillController {

    private final MarketPriceBackfillService backfillService;
    private final ViopBackfillRunner viopBackfillRunner;

    /**
     * Örnek çağrı:
     * POST /internal/market/backfill/tcmb?days=90
     */
    @PostMapping("/tcmb")
    public ResponseEntity<?> backfillTcmb(
            @RequestParam(name = "days", defaultValue = "90") int days
    ) {
        backfillService.backfillFxLastDays(days);
        return ResponseEntity.ok()
                .body("TCMB FX backfill tamamlandı. days=" + days);
    }

    /**
     * Klasördeki tüm {@code viop_YYYYMMDD.csv} dosyalarını bir kez işler; satırlar DB'de varsa atlanır.
     * Örnek: {@code curl -X POST http://localhost:8083/internal/market/backfill/viop-csv}
     */
    @PostMapping("/viop-csv")
    public ResponseEntity<?> importViopCsvFiles() {
        return ResponseEntity.ok(viopBackfillRunner.importAllCsvFilesToDb());
    }
}