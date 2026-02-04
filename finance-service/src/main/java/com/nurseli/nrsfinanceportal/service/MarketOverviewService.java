package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.dto.*;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketOverviewService {

    private final MarketDataClient marketDataClient;

    public MarketOverviewResponse getOverview() {

        // =====================
        // FX (DÖVİZ)
        // =====================
        var doviz = marketDataClient.getLatestDoviz()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new FxOverviewDto(
                                e.getValue().buyPrice(),
                                e.getValue().sellPrice(),
                                e.getValue().source()
                        ),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // =====================
        // METALS
        // =====================
        var metals = marketDataClient.getLatestMetals()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new MetalOverviewDto(
                                e.getValue().buyPrice(),
                                e.getValue().source()
                        ),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // =====================
        // CRYPTO
        // =====================
        var crypto = marketDataClient.getLatestCrypto()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new CryptoOverviewDto(
                                e.getValue().buyPrice(),
                                e.getValue().source()
                        ),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // =====================
        // 🔥 TEFAS FONLARI (GERÇEK VERİ)
        // =====================
        var funds = marketDataClient.getLatestFunds()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, // AES, AFT, TCD...
                        e -> new FundOverviewDto(
                                e.getValue().buyPrice(), // fon fiyatı
                                e.getValue().source()    // TEFAS
                        ),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // =====================
        // OVERVIEW RESPONSE
        // =====================
        return new MarketOverviewResponse(
                doviz,
                metals,
                crypto,
                funds,
                LocalDateTime.now()
        );
    }
}