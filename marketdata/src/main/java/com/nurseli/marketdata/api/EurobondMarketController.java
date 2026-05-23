package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.eurobond.EurobondBatchHistoryResponse;
import com.nurseli.marketdata.api.dto.eurobond.EurobondHistoryResponse;
import com.nurseli.marketdata.api.dto.eurobond.EurobondOverviewResponse;
import com.nurseli.marketdata.application.eurobond.EurobondEvdsQueryService;
import com.nurseli.marketdata.application.eurobond.EurobondInstrumentIngestService;
import com.nurseli.marketdata.application.eurobond.EurobondInstrumentQueryService;
import com.nurseli.marketdata.config.ConditionalOnEurobondEvds;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/market/eurobonds")
@ConditionalOnEurobondEvds
@RequiredArgsConstructor
public class EurobondMarketController {

    private final EurobondEvdsQueryService queryService;
    private final EurobondInstrumentQueryService instrumentQueryService;
    private final EurobondInstrumentIngestService instrumentIngestService;

    @GetMapping("/overview")
    public ResponseEntity<EurobondOverviewResponse> overview() {
        return ResponseEntity.ok(queryService.overview());
    }

    @GetMapping("/history")
    public ResponseEntity<EurobondHistoryResponse> history(
            @RequestParam String series,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(queryService.history(series, from, to));
    }

    @GetMapping("/instruments/seed-sync")
    public ResponseEntity<com.nurseli.marketdata.api.dto.eurobond.EurobondInstrumentIngestResponse> seedSync() {
        var r = instrumentIngestService.ingestDefaultLookback();
        return ResponseEntity.ok(new com.nurseli.marketdata.api.dto.eurobond.EurobondInstrumentIngestResponse(
                r.instrumentsTouched(),
                r.pointsUpserted(),
                r.pointsSkipped(),
                java.time.LocalDate.now().minusYears(5),
                java.time.LocalDate.now(),
                r.status()));
    }

    @GetMapping("/instruments/catalog")
    public ResponseEntity<com.nurseli.marketdata.api.dto.eurobond.EurobondInstrumentListResponse> instrumentCatalog() {
        return ResponseEntity.ok(instrumentQueryService.catalog());
    }

    @GetMapping("/instruments/latest")
    public ResponseEntity<com.nurseli.marketdata.api.dto.eurobond.EurobondInstrumentLatestResponse> instrumentLatest() {
        return ResponseEntity.ok(instrumentQueryService.latest());
    }

    @GetMapping("/instruments/history")
    public ResponseEntity<com.nurseli.marketdata.api.dto.eurobond.EurobondInstrumentHistoryResponse> instrumentHistory(
            @RequestParam String isin,
            @RequestParam(defaultValue = "365") int days) {
        return ResponseEntity.ok(instrumentQueryService.history(isin, days));
    }

    @GetMapping("/batch-history")
    public ResponseEntity<EurobondBatchHistoryResponse> batchHistory(
            @RequestParam String series,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<String> codes = parseSeriesList(series);
        return ResponseEntity.ok(queryService.batchHistory(codes, from, to));
    }

    private static List<String> parseSeriesList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
