package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.eurobond.EurobondBackfillResponse;
import com.nurseli.marketdata.api.dto.eurobond.EurobondInstrumentIngestResponse;
import com.nurseli.marketdata.application.eurobond.EurobondEvdsIngestService;
import com.nurseli.marketdata.application.eurobond.EurobondInstrumentIngestService;
import com.nurseli.marketdata.config.ConditionalOnEurobondEvds;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping({"/api/v1/admin/market/eurobonds/evds", "/api/admin/market/eurobonds/evds"})
@ConditionalOnEurobondEvds
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPS')")
public class EurobondAdminController {

    private final EurobondEvdsProperties properties;
    private final EurobondEvdsIngestService ingestService;
    private final EurobondInstrumentIngestService instrumentIngestService;

    @PostMapping("/backfill")
    public EurobondBackfillResponse backfill(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        if (!properties.isEnabled()) {
            return new EurobondBackfillResponse(0, 0, 0, from, end, "eurobond-evds disabled");
        }
        if (from == null || end.isBefore(from)) {
            return new EurobondBackfillResponse(0, 0, 0, from, end, "invalid range");
        }
        EurobondEvdsIngestService.EurobondIngestResult r = ingestService.ingestRange(from, end);
        return new EurobondBackfillResponse(
                r.seriesTouched(), r.pointsUpserted(), r.pointsSkipped(), from, end, r.status());
    }

    @PostMapping("/instruments/backfill")
    public EurobondInstrumentIngestResponse instrumentBackfill(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        if (!properties.isEnabled() || !properties.getInstruments().isEnabled()) {
            return new EurobondInstrumentIngestResponse(0, 0, 0, from, end, "instruments-disabled");
        }
        if (from == null || end.isBefore(from)) {
            return new EurobondInstrumentIngestResponse(0, 0, 0, from, end, "invalid-range");
        }
        var r = instrumentIngestService.ingestHistory(from, end);
        return new EurobondInstrumentIngestResponse(
                r.instrumentsTouched(), r.pointsUpserted(), r.pointsSkipped(), from, end, r.status());
    }
}
