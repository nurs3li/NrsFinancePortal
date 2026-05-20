package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.eurobond.EurobondBatchHistoryResponse;
import com.nurseli.marketdata.api.dto.eurobond.EurobondHistoryResponse;
import com.nurseli.marketdata.api.dto.eurobond.EurobondOverviewResponse;
import com.nurseli.marketdata.application.eurobond.EurobondEvdsQueryService;
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
@RequiredArgsConstructor
public class EurobondMarketController {

    private final EurobondEvdsQueryService queryService;

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
