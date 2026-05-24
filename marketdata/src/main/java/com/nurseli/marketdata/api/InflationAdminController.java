package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.inflation.InflationBackfillResponse;
import com.nurseli.marketdata.api.dto.inflation.InflationPpiSyncResponse;
import com.nurseli.marketdata.application.inflation.InflationIndexIngestService;
import com.nurseli.marketdata.application.inflation.InflationPpiIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping({"/api/v1/admin/market/inflation", "/api/admin/market/inflation"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPS')")
public class InflationAdminController {

    private final InflationIndexIngestService inflationIndexIngestService;
    private final InflationPpiIngestService inflationPpiIngestService;

    /**
     * TÜFE + Yİ-ÜFE endeks serilerini EVDS'ten çekip DB'ye upsert eder; inflation Redis önbelleğini temizler.
     */
    @PostMapping("/backfill")
    public InflationBackfillResponse backfill(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "true") boolean cpi,
            @RequestParam(defaultValue = "true") boolean ppi
    ) {
        LocalDate toEffective = to != null ? to : LocalDate.now();
        return inflationIndexIngestService.backfill(from, toEffective, cpi, ppi);
    }

    /** Geriye dönük uyumluluk — yalnızca Yİ-ÜFE. */
    @PostMapping("/ppi/sync")
    public InflationPpiSyncResponse syncPpi(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate toEffective = to != null ? to : LocalDate.now();
        InflationPpiIngestService.InflationPpiSyncResult r = inflationPpiIngestService.syncRange(from, toEffective);
        return new InflationPpiSyncResponse(
                r.monthsProcessed(),
                r.rowsUpserted(),
                r.from(),
                r.to(),
                r.status()
        );
    }
}
