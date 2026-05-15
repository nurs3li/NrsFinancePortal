package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.inflation.InflationPpiSyncResponse;
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
@RequestMapping("/api/admin/market/inflation/ppi")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPS')")
public class InflationAdminController {

    private final InflationPpiIngestService inflationPpiIngestService;

    @PostMapping("/sync")
    public InflationPpiSyncResponse sync(
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
