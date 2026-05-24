package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.deposit.DepositRateHistoryRowDto;
import com.nurseli.marketdata.api.dto.deposit.DepositRateLatestRowDto;
import com.nurseli.marketdata.api.dto.deposit.DepositRateSeriesMetaDto;
import com.nurseli.marketdata.application.deposit.DepositRatesQueryService;
import com.nurseli.marketdata.config.DepositRatesProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/market/macro/deposit-rates", "/api/market/macro/deposit-rates"})
@RequiredArgsConstructor
public class DepositRatesMarketController {

    private final DepositRatesProperties depositRatesProperties;
    private final DepositRatesQueryService depositRatesQueryService;

    @GetMapping("/latest")
    public ResponseEntity<List<DepositRateLatestRowDto>> latest() {
        if (!depositRatesProperties.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(depositRatesQueryService.latestAll());
    }

    @GetMapping("/history")
    public ResponseEntity<List<DepositRateHistoryRowDto>> history(
            @RequestParam String currency,
            @RequestParam String term,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (!depositRatesProperties.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (to.isBefore(from)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(depositRatesQueryService.history(currency, term, from, to));
    }

    @GetMapping("/series")
    public ResponseEntity<List<DepositRateSeriesMetaDto>> series(@RequestParam String currency) {
        if (!depositRatesProperties.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(depositRatesQueryService.seriesForCurrency(currency));
    }
}
