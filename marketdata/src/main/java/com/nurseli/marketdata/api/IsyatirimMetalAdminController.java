package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.IsyatirimMetalBackfillResponse;
import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.application.ingest.IsyatirimMetalUsdBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping({"/api/v1/admin/market/metals/isyatirim", "/api/admin/market/metals/isyatirim"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPS')")
public class IsyatirimMetalAdminController {

    private final IsyatirimMetalUsdBackfillService backfillService;

    @PostMapping("/backfill")
    public IsyatirimMetalBackfillResponse backfill(
            @RequestParam(required = false) String symbols,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean force
    ) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidRequestException("from > to");
        }
        return backfillService.run(symbols, from, to, force);
    }
}
