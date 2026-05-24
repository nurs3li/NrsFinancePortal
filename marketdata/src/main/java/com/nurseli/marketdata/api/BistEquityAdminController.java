package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.BistBackfillRequest;
import com.nurseli.marketdata.api.dto.BistBackfillResponse;
import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.application.bist.BistEquityBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BIST günlük (HisseTekil) manuel backfill — {@code BistEquityMarketAdminController} intraday/OneEndeks ile karıştırılmamalıdır.
 */
@RestController
@RequestMapping({"/api/v1/admin/market/equities/bist", "/api/admin/market/equities/bist"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPS')")
public class BistEquityAdminController {

    private final BistEquityBackfillService bistEquityBackfillService;

    @PostMapping("/backfill")
    public BistBackfillResponse backfill(@RequestBody(required = false) BistBackfillRequest body) {
        BistBackfillRequest req = body != null ? body : new BistBackfillRequest();
        if (req.getFrom() != null && req.getTo() != null && req.getTo().isBefore(req.getFrom())) {
            throw new InvalidRequestException("from > to");
        }
        return bistEquityBackfillService.runBackfill(req);
    }
}
