package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.ViopContractResponse;
import com.nurseli.marketdata.api.dto.ViopMarketWatchResponse;
import com.nurseli.marketdata.api.dto.ViopSnapshotResponse;
import com.nurseli.marketdata.application.ViopQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/viop")
@RequiredArgsConstructor
public class ViopMarketController {
    private final ViopQueryService viopQueryService;

    @GetMapping("/contracts")
    public List<ViopContractResponse> contracts() {
        return viopQueryService.contracts();
    }

    @GetMapping("/latest")
    public List<ViopSnapshotResponse> latest() {
        return viopQueryService.latest();
    }

    @GetMapping("/history")
    public List<ViopSnapshotResponse> history(
            @RequestParam String contract,
            @RequestParam(defaultValue = "7") int days
    ) {
        return viopQueryService.history(contract, days);
    }

    @GetMapping("/oi-history")
    public List<ViopSnapshotResponse> oiHistory(
            @RequestParam String contract,
            @RequestParam(defaultValue = "7") int days
    ) {
        return viopQueryService.oiHistory(contract, days);
    }

    @GetMapping("/market-watch")
    public ViopMarketWatchResponse marketWatch() {
        return viopQueryService.marketWatch();
    }
}
