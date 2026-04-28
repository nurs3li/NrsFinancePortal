package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.DebtInstrumentResponse;
import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.application.DebtQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/debt")
@RequiredArgsConstructor
public class DebtMarketController {
    private final DebtQueryService debtQueryService;

    @GetMapping("/catalog")
    public List<DebtInstrumentResponse> catalog() {
        return debtQueryService.catalog();
    }

    @GetMapping("/latest")
    public List<DebtSnapshotResponse> latest() {
        return debtQueryService.latest();
    }

    @GetMapping("/history")
    public List<DebtSnapshotResponse> history(
            @RequestParam String isin,
            @RequestParam(defaultValue = "7") int days
    ) {
        return debtQueryService.history(isin, days);
    }
}
