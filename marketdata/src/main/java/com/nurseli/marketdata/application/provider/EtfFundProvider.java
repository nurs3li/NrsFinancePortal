package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.DataQualityFlag;
import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.application.SpreadCalculator;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import com.nurseli.marketdata.infrastructure.finhub.FinHubCandleDto;
import com.nurseli.marketdata.infrastructure.finhub.FinHubClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EtfFundProvider implements FundProvider {
    private final MarketPriceQueryService queryService;
    private final FinHubClient finHubClient;

    public EtfFundProvider(MarketPriceQueryService queryService, FinHubClient finHubClient) {
        this.queryService = queryService;
        this.finHubClient = finHubClient;
    }

    @Override
    public Map<String, MarketPriceLatestResponse> getLatest() {
        return queryService.getLatestFunds();
    }

    @Override
    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(Math.max(1, days));
        long fromEpoch = start.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        long toEpoch = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() - 1;
        FinHubCandleDto candles = finHubClient.fetchDailyCandles(symbol, fromEpoch, toEpoch).block();
        if (candles == null || candles.getT() == null || candles.getC() == null || candles.getT().isEmpty()) {
            return queryService.getHistory(symbol, days);
        }
        if (!"ok".equalsIgnoreCase(candles.getS())) {
            return queryService.getHistory(symbol, days);
        }
        int size = Math.min(candles.getT().size(), candles.getC().size());
        List<MarketPriceHistoryResponse> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Long epoch = candles.getT().get(i);
            Double close = candles.getC().get(i);
            if (epoch == null || close == null || close <= 0) {
                continue;
            }
            BigDecimal mid = BigDecimal.valueOf(close);
            LocalDate day = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDateTime ts = LocalDateTime.of(day, java.time.LocalTime.NOON);
            out.add(new MarketPriceHistoryResponse(
                    SpreadCalculator.buyPrice(mid),
                    SpreadCalculator.sellPrice(mid),
                    ts,
                    "ETF",
                    ts,
                    DataQualityFlag.EXACT
            ));
        }
        return out.isEmpty() ? queryService.getHistory(symbol, days) : out;
    }

    @Override
    public String providerName() {
        return "ETF";
    }

    @Override
    public boolean isCanonical() {
        return false;
    }
}
