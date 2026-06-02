package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.BistBackfillRequest;
import com.nurseli.marketdata.api.dto.BistBackfillResponse;
import com.nurseli.marketdata.api.dto.CryptoHistoryWarmupResponse;
import com.nurseli.marketdata.api.dto.DebtHistoryWarmupResponse;
import com.nurseli.marketdata.api.dto.IsyatirimMetalBackfillResponse;
import com.nurseli.marketdata.application.ingest.CryptoHistoryWarmupService;
import com.nurseli.marketdata.application.ingest.DebtHistoryWarmupService;
import com.nurseli.marketdata.application.ingest.FundPriceIngestService;
import com.nurseli.marketdata.application.ingest.IsyatirimMetalUsdBackfillService;
import com.nurseli.marketdata.application.ingest.MarketPriceBackfillService;
import com.nurseli.marketdata.application.ingest.ViopBackfillRunner;
import com.nurseli.marketdata.application.bist.BistEquityBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
    private final CryptoHistoryWarmupService cryptoHistoryWarmupService;
    private final DebtHistoryWarmupService debtHistoryWarmupService;
    private final FundPriceIngestService fundPriceIngestService;

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
        if (!hasValidInternalToken(headerToken)) {
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
        if (!hasValidInternalToken(headerToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        IsyatirimMetalBackfillResponse resp =
                isyatirimMetalUsdBackfillService.run(symbols, from, to, force);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/crypto-history")
    public ResponseEntity<?> warmupCryptoHistory(
            @RequestHeader(value = INTERNAL_BACKFILL_HEADER, required = false) String headerToken,
            @RequestParam(required = false) String symbols,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "manual") String reason
    ) {
        if (!hasValidInternalToken(headerToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<String> symbolList = symbols == null || symbols.isBlank()
                ? List.of()
                : Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        CryptoHistoryWarmupResponse resp =
                cryptoHistoryWarmupService.requestWarmup(symbolList, from, to, reason);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/debt-history")
    public ResponseEntity<?> warmupDebtHistory(
            @RequestHeader(value = INTERNAL_BACKFILL_HEADER, required = false) String headerToken,
            @RequestParam(required = false) String isins,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "manual") String reason
    ) {
        if (!hasValidInternalToken(headerToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<String> isinList = isins == null || isins.isBlank()
                ? List.of()
                : Arrays.stream(isins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        DebtHistoryWarmupResponse resp =
                debtHistoryWarmupService.requestWarmup(isinList, from, to, reason);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/etf-history")
    public ResponseEntity<?> backfillEtfHistory(
            @RequestHeader(value = INTERNAL_BACKFILL_HEADER, required = false) String headerToken,
            @RequestParam(required = false) String symbols,
            @RequestParam(name = "days", defaultValue = "365") int days
    ) {
        if (!hasValidInternalToken(headerToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<String> symbolList = symbols == null || symbols.isBlank()
                ? List.of()
                : Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        int inserted = fundPriceIngestService.ingestHistoryForSymbols(symbolList, days);
        int symbolCount = symbolList.isEmpty() ? 0 : symbolList.size();
        return ResponseEntity.ok(Map.of(
                "status", "DONE",
                "requestedSymbols", symbolCount,
                "days", days,
                "insertedRows", inserted
        ));
    }

    private boolean hasValidInternalToken(String headerToken) {
        String configuredToken = resolveInternalBackfillToken();
        return configuredToken != null
                && !configuredToken.isBlank()
                && headerToken != null
                && configuredToken.equals(headerToken);
    }

    private String resolveInternalBackfillToken() {
        if (internalBackfillToken != null && !internalBackfillToken.isBlank()) {
            return internalBackfillToken;
        }
        String env = System.getenv("NRS_INTERNAL_BACKFILL_TOKEN");
        return env != null && !env.isBlank() ? env.trim() : internalBackfillToken;
    }
}