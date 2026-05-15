package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.deposit.DepositRatesBackfillResponse;
import com.nurseli.marketdata.application.deposit.DepositRatesIngestService;
import com.nurseli.marketdata.config.DepositRatesProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/market/deposit-rates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPS')")
public class DepositRatesAdminController {

    private final DepositRatesProperties depositRatesProperties;
    private final DepositRatesIngestService depositRatesIngestService;

    @PostMapping("/backfill")
    public DepositRatesBackfillResponse backfill(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (!depositRatesProperties.isEnabled()) {
            return new DepositRatesBackfillResponse(0, 0, 0, from, to, "deposit-rates disabled");
        }
        if (to.isBefore(from)) {
            return new DepositRatesBackfillResponse(0, 0, 0, from, to, "invalid range");
        }
        DepositRatesIngestService.DepositRatesIngestResult r = depositRatesIngestService.ingestRange(from, to);
        return new DepositRatesBackfillResponse(
                r.seriesTouched(),
                r.pointsUpserted(),
                r.pointsSkipped(),
                from,
                to,
                r.status()
        );
    }
}
