package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.inflation.InflationCompareResponse;
import com.nurseli.marketdata.api.dto.inflation.InflationHistoryResponse;
import com.nurseli.marketdata.api.dto.inflation.InflationLatestResponse;
import com.nurseli.marketdata.application.inflation.InflationMacroService;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping({"/api/v1/market/macro/inflation", "/api/market/macro/inflation"})
@RequiredArgsConstructor
public class InflationMacroController {

    private final InflationMacroService inflationMacroService;

    @GetMapping("/latest")
    public ResponseEntity<InflationLatestResponse> latest() {
        return ResponseEntity.ok(inflationMacroService.latest());
    }

    @GetMapping("/history")
    public ResponseEntity<InflationHistoryResponse> history(
            @RequestParam String type,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth to
    ) {
        InflationIndicatorType indicatorType = InflationIndicatorType.fromApi(type).orElse(null);
        if (indicatorType == null || to.isBefore(from)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(inflationMacroService.history(indicatorType, from, to));
    }

    @GetMapping("/compare")
    public ResponseEntity<InflationCompareResponse> compare(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth to
    ) {
        if (to.isBefore(from)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(inflationMacroService.compare(from, to));
    }
}
