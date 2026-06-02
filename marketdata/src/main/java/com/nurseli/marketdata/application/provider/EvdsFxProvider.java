package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.DataQualityFlag;
import com.nurseli.marketdata.api.dto.PriceQuality;
import com.nurseli.marketdata.application.query.MarketPriceQueryService;
import com.nurseli.marketdata.infrastructure.evds.EvdsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
@Slf4j
public class EvdsFxProvider implements FxProvider {

    private final EvdsClient evdsClient;
    private final MarketPriceQueryService queryService;

    public EvdsFxProvider(EvdsClient evdsClient, MarketPriceQueryService queryService) {
        this.evdsClient = evdsClient;
        this.queryService = queryService;
    }

    @Override
    public Map<String, MarketPriceLatestResponse> getLatest() {
        Map<String, MarketPriceLatestResponse> latest = queryService.getLatestBySource("EVDS");
        Map<String, MarketPriceLatestResponse> out = new LinkedHashMap<>();
        latest.forEach((symbol, last) -> out.put(symbol, new MarketPriceLatestResponse(
                    last.symbol(),
                    last.buyPrice(),
                    last.sellPrice(),
                    "EVDS",
                    last.timestamp(),
                    last.asOf() != null ? last.asOf() : last.timestamp(),
                    PriceQuality.FALLBACK,
                    last.marketCap(),
                    last.marketCapSource(),
                    last.marketCapAsOf()
            )));
        return out;
    }

    @Override
    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        List<EvdsClient.EvdsFxPoint> points = evdsClient.getHistoricalFx(symbol, start, end);
        return points.stream().map(p -> new MarketPriceHistoryResponse(
                p.price(),
                p.price(),
                p.timestamp(),
                "EVDS",
                p.timestamp(),
                DataQualityFlag.EXACT
        )).toList();
    }

    @Override
    public String providerName() {
        return "EVDS";
    }

    @Override
    public boolean isCanonical() {
        return false;
    }

    @Override
    public boolean isHealthy() {
        return !getLatest().isEmpty();
    }
}
