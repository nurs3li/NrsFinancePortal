package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataInternalBackfillGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Admin arayüzünden market-data backfill — tarayıcı doğrudan 8083 JWT istemez;
 * finance (ADMIN/OPS) doğrulaması sonrası {@code X-Nrs-Internal-Token} ile iç ağa iletir.
 */
@RestController
@RequestMapping("/api/admin/market")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPS')")
public class AdminMarketDataBackfillController {

    private final MarketDataInternalBackfillGateway marketDataInternalBackfillGateway;

    @PostMapping("/metals/isyatirim/backfill")
    public ResponseEntity<?> isyatirimMetalsUsdBackfill(
            @RequestParam(required = false) String symbols,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean force
    ) {
        try {
            Object data = marketDataInternalBackfillGateway.triggerIsyatirimMetalsUsd(symbols, from, to, force);
            return ResponseEntity.ok(data);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503)
                    .body(Map.of(
                            "error", "market_backfill_unavailable",
                            "message", ex.getMessage() != null ? ex.getMessage() : "Backfill yapılandırması eksik."
                    ));
        }
    }

    @PostMapping("/equities/bist/backfill")
    public ResponseEntity<?> bistDailyBackfill(@RequestBody(required = false) Map<String, Object> body) {
        try {
            Object data = marketDataInternalBackfillGateway.triggerBistDaily(body);
            return ResponseEntity.ok(data);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503)
                    .body(Map.of(
                            "error", "market_backfill_unavailable",
                            "message", ex.getMessage() != null ? ex.getMessage() : "Backfill yapılandırması eksik."
                    ));
        }
    }
}
