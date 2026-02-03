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

        var doviz = marketDataClient.getLatestDoviz()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new FxOverviewDto(
                                e.getValue().buyPrice(),
                                e.getValue().sellPrice(),
                                e.getValue().source()
                        )
                ));

        var metals = marketDataClient.getLatestMetals()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new MetalOverviewDto(
                                e.getValue().buyPrice(),
                                // gram altın
                                e.getValue().source()
                        )
                ));

        var crypto = marketDataClient.getLatestCrypto()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new CryptoOverviewDto(
                                e.getValue().buyPrice(),
                                e.getValue().source()
                        )
                ));
// 🔒 FONLAR ŞİMDİLİK SAHTE / PLACEHOLDER
        Map<String, FundOverviewDto> funds = new LinkedHashMap<>();
        funds.put("TCD", new FundOverviewDto(null, "TEFAS"));
        funds.put("AES", new FundOverviewDto(null, "TEFAS"));
        funds.put("TI2", new FundOverviewDto(null, "TEFAS"));

        return new MarketOverviewResponse(
                doviz,
                metals,
                crypto,
                funds,
                LocalDateTime.now()
        );
    }
}