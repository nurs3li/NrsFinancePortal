package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.loan.LoanRatesHistoryResponse;
import com.nurseli.marketdata.api.dto.loan.LoanRatesLatestResponse;
import com.nurseli.marketdata.application.loan.LoanRatesMacroService;
import com.nurseli.marketdata.domain.loan.LoanRateSubtype;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping({"/api/v1/market/macro/loan-rates", "/api/market/macro/loan-rates"})
@RequiredArgsConstructor
public class LoanRatesMarketController {

    private final LoanRatesMacroService loanRatesMacroService;

    @GetMapping("/latest")
    public ResponseEntity<LoanRatesLatestResponse> latest() {
        return ResponseEntity.ok(loanRatesMacroService.latest());
    }

    @GetMapping("/history")
    public ResponseEntity<LoanRatesHistoryResponse> history(
            @RequestParam(required = false) String types,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Set<LoanRateSubtype> set = LoanRateSubtype.parseMany(types);
        return ResponseEntity.ok(loanRatesMacroService.history(set, from, to));
    }
}
