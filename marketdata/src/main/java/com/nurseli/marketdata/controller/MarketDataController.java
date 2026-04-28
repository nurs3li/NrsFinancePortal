package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import com.nurseli.marketdata.application.provider.FxProviderFacade;
import com.nurseli.marketdata.application.provider.ProviderRegistry;
import com.nurseli.marketdata.application.provider.FxProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/doviz")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketPriceQueryService queryService;
    private final ProviderRegistry providerRegistry;
    private final FxProviderFacade fxProviderFacade;

    private static void putLatestIfPresent(
            Map<String, MarketPriceLatestResponse> map,
            String symbol,
            MarketPriceLatestResponse row
    ) {
        if (row != null) {
            map.put(symbol, row);
        }
    }

    // =========================
    // 🔹 DASHBOARD – LATEST (FX)
    // =========================
    private static final List<String> FX_LATEST_TRIPLET = List.of("USDTRY", "EURTRY", "GBPTRY");

    @GetMapping("/latest")
    public Map<String, Object> latest(
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "false") boolean compare
    ) {
        FxProvider selected = providerRegistry.fxByNameOrCanonical(provider);
        // Tek seferde seçilen sağlayıcının haritası (ör. TCMB: DB + gerekiyorsa tek XML çekimi).
        // Önceki sürümde her sembol için facade çağrılıyordu → TcmbFxProvider.getLatest() 3 kez ve 3 kez canlı XML.
        Map<String, MarketPriceLatestResponse> batch = selected.getLatest();
        Map<String, MarketPriceLatestResponse> selectedData = new LinkedHashMap<>();
        for (String sym : FX_LATEST_TRIPLET) {
            MarketPriceLatestResponse row = batch.get(sym);
            if (row == null) {
                row = fxProviderFacade.getLatest(sym);
            }
            putLatestIfPresent(selectedData, sym, row);
        }
        if (!compare) {
            return new LinkedHashMap<>(selectedData);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider", selected.providerName());
        response.put("canonical", selected.isCanonical());
        response.put("data", selectedData);
        if (compare) {
            response.put("canonicalData", providerRegistry.fxCanonical().getLatest());
        }
        return response;
    }

    // =========================
    // 🔹 HISTORY – TIME BUCKET
    // =========================
    @GetMapping("/history")
    public List<MarketPriceHistoryResponse> history(
            @RequestParam String symbol,
            @RequestParam int days,
            @RequestParam(required = false) String provider
    ) {
        FxProvider selected = providerRegistry.fxByNameOrCanonical(provider);
        List<MarketPriceHistoryResponse> result = selected.getHistory(symbol, days);
        if (!result.isEmpty()) {
            return result;
        }
        for (FxProvider fallback : providerRegistry.fxFallbackOrder()) {
            List<MarketPriceHistoryResponse> fallbackHistory = fallback.getHistory(symbol, days);
            if (!fallbackHistory.isEmpty()) {
                return fallbackHistory;
            }
        }
        return queryService.getHistory(symbol, days);
    }
}