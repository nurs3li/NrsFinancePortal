package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.BistBatchHistoryResponse;
import com.nurseli.marketdata.api.dto.BistEquityCandleResponse;
import com.nurseli.marketdata.api.dto.BistEquityHistoryResponse;
import com.nurseli.marketdata.api.dto.BistEquityLatestResponse;
import com.nurseli.marketdata.api.dto.BistSymbolResponse;
import com.nurseli.marketdata.api.dto.PagedResponse;
import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.application.bist.BistEquityDailyConstants;
import com.nurseli.marketdata.application.bist.BistEquityQueryService;
import com.nurseli.marketdata.config.BistProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * BIST Türk hissesi günlük (İş Yatırım HisseTekil) okuma uçları — intraday {@code /api/market/equity} ile ayrıdır.
 */
@RestController
@RequestMapping({"/api/v1/market/equities/bist", "/api/market/equities/bist"})
@RequiredArgsConstructor
public class BistEquityController {

    private final BistEquityQueryService bistEquityQueryService;
    private final BistProperties bistProperties;

    @GetMapping("/symbols")
    public List<BistSymbolResponse> symbols() {
        return bistEquityQueryService.getSymbols();
    }

    @GetMapping("/latest")
    public List<BistEquityLatestResponse> latestAll() {
        return bistEquityQueryService.getLatest();
    }

    @GetMapping("/latest/page")
    public PagedResponse<BistEquityLatestResponse> latestPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "changePercent") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(required = false) String search) {
        return bistEquityQueryService.getLatestPage(page, size, sort, dir, filter, search);
    }

    @GetMapping("/batch-history")
    public BistBatchHistoryResponse batchHistory(
            @RequestParam String symbols,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (symbols == null || symbols.isBlank()) {
            throw new InvalidRequestException("symbols zorunludur");
        }
        List<String> list =
                Arrays.stream(symbols.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (list.isEmpty()) {
            throw new InvalidRequestException("symbols zorunludur");
        }
        LocalDateRange range = resolveRange(from, to);
        return bistEquityQueryService.getBatchHistory(list, range.from(), range.to());
    }

    @GetMapping("/{symbol}/latest")
    public ResponseEntity<BistEquityLatestResponse> latestOne(@PathVariable String symbol) {
        return bistEquityQueryService
                .getLatest(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().build());
    }

    @GetMapping("/{symbol}/history")
    public List<BistEquityHistoryResponse> history(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDateRange range = resolveRange(from, to);
        return bistEquityQueryService.getHistory(symbol, range.from(), range.to());
    }

    @GetMapping("/{symbol}/candles")
    public List<BistEquityCandleResponse> candles(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDateRange range = resolveRange(from, to);
        return bistEquityQueryService.getCandles(symbol, range.from(), range.to());
    }

    private LocalDateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate toD = to != null ? to : LocalDate.now(BistEquityDailyConstants.IST);
        LocalDate fromD =
                from != null
                        ? from
                        : toD.minusYears(Math.max(1, bistProperties.getDefaultLookbackYears()));
        if (toD.isBefore(fromD)) {
            throw new InvalidRequestException("from > to veya geçersiz tarih aralığı");
        }
        return new LocalDateRange(fromD, toD);
    }

    private record LocalDateRange(LocalDate from, LocalDate to) {}
}
