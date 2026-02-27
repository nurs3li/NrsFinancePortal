package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import com.nurseli.marketdata.domain.price.CryptoSymbolMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/crypto")
@RequiredArgsConstructor
public class CryptoMarketController {

    private final MarketPriceQueryService queryService;

    // =========================
    // 🔹 CRYPTO – LATEST
    // =========================
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latest() {
        Map<String, Object> response = new LinkedHashMap<>();

        // SYMBOL_TO_ID içindeki tüm anahtarları (BTCUSDT, ETHUSDT vb.) döner
        CryptoSymbolMapping.SYMBOL_TO_ID.keySet().forEach(symbol -> {
            response.put(symbol, getSafeLatest(symbol));
        });

        return ResponseEntity.ok(response);
    }

    // =========================
    // 🔹 SAFE FETCH (NO_DATA FALLBACK)
    // =========================
    private Object getSafeLatest(String symbol) {
        try {
            return queryService.getLatestOrThrow(symbol);
        } catch (IllegalStateException ex) {
            return Map.of(
                    "status", "NO_DATA",
                    "message", "Crypto price temporarily unavailable"
            );
        }
    }

    @GetMapping("/history")
    public List<MarketPriceHistoryResponse> history(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "7") int days
    ) {
        return queryService.getHistory(symbol, days);
    }
}