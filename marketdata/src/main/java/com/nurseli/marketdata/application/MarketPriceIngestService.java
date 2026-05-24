package com.nurseli.marketdata.application;

import com.nurseli.marketdata.infrastructure.tcmb.TcmbClient;
import com.nurseli.marketdata.infrastructure.yahoo.YahooChartClient;
import com.nurseli.marketdata.domain.price.FxDailyCandle;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.persistence.FxDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketPriceIngestService {

    private static final ZoneId MARKET_WALL_CLOCK_ZONE = ZoneId.of("Europe/Istanbul");

    private final TcmbClient tcmbClient;
    private final MarketPriceHistoryRepository repository;
    private final TcmbFxSnapshotCache tcmbFxSnapshotCache;
    private final YahooChartClient yahooChartClient;
    private final FxDailyCandleRepository fxDailyCandleRepository;

    /**
     * Yeni FX verisi geldiğinde:
     * - DB yazılır
     * - latest-price + indicators + batch cache temizlenir
     */
    @Transactional
    @CacheEvict(
            cacheNames = {"latest-price", "market:batch", "market:indicators"},
            allEntries = true
    )
    public void fetchAndSaveTcmbRates() {
        var rates = tcmbClient.fetchRates();
        LocalDateTime now = LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
        tcmbFxSnapshotCache.replaceFromTcmbRates(rates, now);

        rates.forEach(rate -> {

            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(rate.symbol() + "TRY");
            entity.setBuyPrice(rate.buy());
            entity.setSellPrice(rate.sell());
            entity.setSource("TCMB");
            entity.setTimestamp(now);

            repository.save(entity);
        });
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveFxHistoryBackfill(int periodDays) {
        String range = periodDays >= 730 ? "2y" : "1y";
        List<String> symbols = List.of("USDTRY", "EURTRY", "GBPTRY");
        for (String symbol : symbols) {
            List<YahooChartClient.YahooDailyBar> bars = yahooChartClient.fetchDailyBars(symbol, range);
            int inserted = 0;
            for (YahooChartClient.YahooDailyBar bar : bars) {
                if (fxDailyCandleRepository.existsBySymbolAndAsOf(symbol, bar.day())) {
                    continue;
                }
                FxDailyCandle candle = new FxDailyCandle();
                candle.setSymbol(symbol);
                candle.setAsOf(bar.day());
                candle.setOpenPrice(bar.open());
                candle.setHighPrice(bar.high());
                candle.setLowPrice(bar.low());
                candle.setClosePrice(bar.close());
                candle.setVolume(bar.volume());
                candle.setSource("YAHOO_FX_OHLC");
                fxDailyCandleRepository.save(candle);
                inserted++;
            }
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveFxHistoryIncremental() {
        fetchAndSaveFxHistoryBackfill(365);
    }
}