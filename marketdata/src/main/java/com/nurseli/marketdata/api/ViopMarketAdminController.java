package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.ViopHistoryResponse;
import com.nurseli.marketdata.api.dto.ViopMarketSnapshotDto;
import com.nurseli.marketdata.application.ViopMarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping({"/api/v1/admin/market/viop", "/api/admin/market/viop"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPS')")
public class ViopMarketAdminController {

    private final ViopMarketDataService viopMarketDataService;

    @PostMapping("/contracts/{contractCode}/refresh-snapshot")
    public ViopMarketSnapshotDto refreshSnapshot(@PathVariable String contractCode) {
        return viopMarketDataService.refreshSnapshot(contractCode);
    }

    @PostMapping("/contracts/{contractCode}/refresh-history")
    public ViopHistoryResponse refreshHistory(
            @PathVariable String contractCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "60") int period) {
        return viopMarketDataService.refreshHistory(contractCode, from, to, period);
    }

    @PostMapping("/refresh-all-snapshots")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refreshAllSnapshots() {
        viopMarketDataService.refreshAllSnapshots();
    }
}
