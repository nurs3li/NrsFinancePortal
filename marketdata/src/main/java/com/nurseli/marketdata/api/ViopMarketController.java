package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.ViopMarketContractDto;
import com.nurseli.marketdata.api.dto.ViopMarketSnapshotDto;
import com.nurseli.marketdata.api.dto.ViopMarketWatchResponse;
import com.nurseli.marketdata.api.dto.ViopPriceAtResponse;
import com.nurseli.marketdata.api.dto.ViopHistoryResponse;
import com.nurseli.marketdata.api.dto.ViopSnapshotResponse;
import com.nurseli.marketdata.application.viop.ViopMarketDataService;
import com.nurseli.marketdata.application.viop.ViopQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/market/viop", "/api/market/viop"})
@RequiredArgsConstructor
public class ViopMarketController {
    private final ViopQueryService viopQueryService;
    private final ViopMarketDataService viopMarketDataService;

    @GetMapping("/contracts")
    public List<ViopMarketContractDto> contracts(@RequestParam(defaultValue = "false") boolean includeExpired) {
        return viopMarketDataService.listContracts(includeExpired);
    }

    @GetMapping("/contracts/{contractCode}")
    public ViopMarketContractDto contract(@PathVariable String contractCode) {
        return viopMarketDataService.getContract(contractCode);
    }

    @GetMapping("/contracts/{contractCode}/snapshot")
    public ViopMarketSnapshotDto contractSnapshot(@PathVariable String contractCode) {
        return viopMarketDataService.getSnapshot(contractCode);
    }

    @GetMapping("/contracts/{contractCode}/history")
    public ViopHistoryResponse contractHistory(
            @PathVariable String contractCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "60") int period) {
        return viopMarketDataService.getHistory(contractCode, from, to, period);
    }

    @GetMapping("/contracts/{contractCode}/price-at")
    public ViopPriceAtResponse priceAt(@PathVariable String contractCode, @RequestParam String date) {
        String d = date.trim();
        try {
            if (d.length() <= 10 && !d.contains("T")) {
                return viopMarketDataService.getPriceAt(contractCode, LocalDate.parse(d));
            }
            return viopMarketDataService.getPriceAt(contractCode, LocalDateTime.parse(d));
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid date; use ISO-8601 date or local date-time", ex);
        }
    }

    @GetMapping("/latest")
    public List<ViopSnapshotResponse> latest() {
        return viopQueryService.latest();
    }

    @GetMapping("/history")
    public List<ViopSnapshotResponse> history(
            @RequestParam String contract, @RequestParam(defaultValue = "7") int days) {
        return viopQueryService.history(contract, days);
    }

    @GetMapping("/oi-history")
    public List<ViopSnapshotResponse> oiHistory(
            @RequestParam String contract, @RequestParam(defaultValue = "7") int days) {
        return viopQueryService.oiHistory(contract, days);
    }

    @GetMapping("/market-watch")
    public ViopMarketWatchResponse marketWatch() {
        return viopQueryService.marketWatch();
    }
}
