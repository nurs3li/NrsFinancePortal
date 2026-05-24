package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.CpiTrMacroResponse;
import com.nurseli.marketdata.api.dto.PolicyRateTrResponse;
import com.nurseli.marketdata.api.dto.TcmbWeightedFundingCostResponse;
import com.nurseli.marketdata.api.dto.TurkeyApproxRealRateResponse;
import com.nurseli.marketdata.api.dto.macropanel.InterestInflationMacroPanelResponse;
import com.nurseli.marketdata.application.EvdsCpiTrService;
import com.nurseli.marketdata.application.EvdsMacroIndicatorService;
import com.nurseli.marketdata.application.macropanel.MacroPanelAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/macro")
@RequiredArgsConstructor
public class MacroMarketController {

    private final EvdsCpiTrService evdsCpiTrService;
    private final EvdsMacroIndicatorService evdsMacroIndicatorService;
    private final MacroPanelAggregationService macroPanelAggregationService;

    @GetMapping("/interest-inflation-panel")
    public ResponseEntity<InterestInflationMacroPanelResponse> interestInflationPanel() {
        return ResponseEntity.ok(macroPanelAggregationService.build());
    }

    @GetMapping("/cpi-tr")
    public ResponseEntity<CpiTrMacroResponse> cpiTr() {
        return evdsCpiTrService.latestCpiTr()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/policy-rate-tr")
    public ResponseEntity<PolicyRateTrResponse> policyRateTr() {
        return evdsMacroIndicatorService.latestPolicyRateTr()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/tcmb-weighted-funding-cost")
    public ResponseEntity<TcmbWeightedFundingCostResponse> tcmbWeightedFundingCost() {
        return evdsMacroIndicatorService.latestTcmbWeightedFundingCost()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/tr-approx-real-rate")
    public ResponseEntity<TurkeyApproxRealRateResponse> trApproxRealRate() {
        return evdsMacroIndicatorService.approxTurkeyRealRate()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
