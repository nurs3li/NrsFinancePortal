package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.BistBackfillRequest;
import com.nurseli.marketdata.api.dto.BistBackfillResponse;
import com.nurseli.marketdata.api.dto.IsyatirimMetalBackfillResponse;
import com.nurseli.marketdata.application.IsyatirimMetalUsdBackfillService;
import com.nurseli.marketdata.application.MarketPriceBackfillService;
import com.nurseli.marketdata.application.ViopBackfillRunner;
import com.nurseli.marketdata.application.bist.BistEquityBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Sadece internal / admin amaçlı backfill endpoint'i.
 * İstersen reverse proxy / network policy ile dış dünyaya kapatabilirsin.
 */
@RestController
@RequestMapping("/internal/market/backfill")
@RequiredArgsConstructor
@Profile("ops")
public class BackfillController {

    private static final String INTERNAL_BACKFILL_HEADER = "X-Nrs-Internal-Token";

    private final MarketPriceBackfillService backfillService;
    private final ViopBackfillRunner viopBackfillRunner;
    private final BistEquityBackfillService bistEquityBackfillService;
    private final IsyatirimMetalUsdBackfillService isyatirimMetalUsdBackfillService;

    @Value("${app.internal.backfill-token:}")
    private String internalBackfillToken;

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

    /**
     * BIST günlük (HisseTekil) tam backfill — {@code BistEquityAdminController} ile aynı servis.
     * Docker / otomasyon: {@code app.internal.backfill-token} doluysa {@code X-Nrs-Internal-Token} başlığı zorunludur;
     * boşsa bu uç 401 döner (JWT ile {@code /api/admin/market/equities/bist/backfill} kullanılır).
     */
    @PostMapping("/bist-daily")
    public ResponseEntity<?> backfillBistDaily(
            @RequestHeader(value = INTERNAL_BACKFILL_HEADER, required = false) String headerToken,
            @RequestBody(required = false) BistBackfillRequest body) {
        if (internalBackfillToken == null
                || internalBackfillToken.isBlank()
                || headerToken == null
                || !internalBackfillToken.equals(headerToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        BistBackfillResponse resp =
                bistEquityBackfillService.runBackfill(body != null ? body : new BistBackfillRequest());
        return ResponseEntity.ok(resp);
    }

    /**
     * İş Yatırım USD/ons kıymetli maden geçmişi — {@code /api/admin/market/metals/isyatirim/backfill} ile aynı servis.
     * {@code app.internal.backfill-token} doluysa {@code X-Nrs-Internal-Token} zorunludur (BIST internal ile aynı anahtar).
     */
    @PostMapping("/isyatirim-metals-usd")
    public ResponseEntity<?> backfillIsyatirimMetalsUsd(
            @RequestHeader(value = INTERNAL_BACKFILL_HEADER, required = false) String headerToken,
            @RequestParam(required = false) String symbols,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean force
    ) {
        if (internalBackfillToken == null
                || internalBackfillToken.isBlank()
                || headerToken == null
                || !internalBackfillToken.equals(headerToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        IsyatirimMetalBackfillResponse resp =
                isyatirimMetalUsdBackfillService.run(symbols, from, to, force);
        return ResponseEntity.ok(resp);
    }
}