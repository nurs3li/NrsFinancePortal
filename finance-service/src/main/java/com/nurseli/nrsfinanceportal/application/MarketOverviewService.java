package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.api.dto.*;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * finance-service market overview servisi — market-data son fiyat snapshot'ını kategori bazlı overview DTO'suna dönüştürür.
 */
@RequiredArgsConstructor
@Service

public class MarketOverviewService {

    private final MarketDataClient marketDataClient;

    /**
     * {@code getOverview} — FX, metal, crypto, fund ve equity son fiyatlarını MarketOverviewResponse olarak döner.
     */
    public MarketOverviewResponse getOverview() {
        var snap = marketDataClient.loadLatestPricing();

        // =====================
        // FX (DÖVİZ)
        // =====================
        var doviz = snap.fx()
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
        var metals = snap.metals()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new MetalOverviewDto(
                                e.getValue().buyPrice(),
                                e.getValue().sellPrice(),
                                e.getValue().source(),
                                e.getValue().timestamp()
                        ),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // =====================
        // CRYPTO
        // =====================
        var crypto = snap.crypto()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new CryptoOverviewDto(
                                e.getValue().buyPrice(),
                                e.getValue().sellPrice(),
                                e.getValue().source(),
                                e.getValue().timestamp()
                        ),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // =====================
        //   FONLAR (ETF – FinHub: SPY, QQQ, VOO...)
        // =====================
        var funds = snap.funds()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, // AES, AFT, TCD...
                        e -> new FundOverviewDto(
                                e.getValue().buyPrice(),
                                e.getValue().sellPrice(),
                                e.getValue().source(),
                                e.getValue().timestamp()
                        ),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        // =====================
        // HİSSELER (FINHUB – EQUITY)
        // =====================
        var stocks = snap.equity()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new StockOverviewDto(
                                e.getValue().buyPrice(),
                                e.getValue().sellPrice(),
                                e.getValue().source(),
                                e.getValue().timestamp(),
                                e.getValue().marketCap(),
                                e.getValue().marketCapSource(),
                                e.getValue().marketCapAsOf()
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
                stocks,
                LocalDateTime.now()
        );    }
}