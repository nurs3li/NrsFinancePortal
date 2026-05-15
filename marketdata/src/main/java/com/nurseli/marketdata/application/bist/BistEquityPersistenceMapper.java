package com.nurseli.marketdata.application.bist;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.bist.BistEquityDailyPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.nurseli.marketdata.application.bist.BistEquityDailyConstants.HISTORY_SOURCE;
import static com.nurseli.marketdata.application.bist.BistEquityDailyConstants.IST;

@Component
public class BistEquityPersistenceMapper {

    public LocalDateTime toHistoryTimestamp(BistEquityDailyPrice row) {
        return row.date().atStartOfDay(IST).toLocalDateTime();
    }

    public String resolveSource(BistEquityDailyPrice row) {
        return row.source() != null ? row.source().name() : HISTORY_SOURCE;
    }

    public MarketPriceHistory toNewEntity(BistEquityDailyPrice row) {
        MarketPriceHistory e = new MarketPriceHistory();
        fill(e, row);
        return e;
    }

    /** Var olan satırı günceller (aynı symbol/source/gün). */
    public void copyOnto(MarketPriceHistory target, BistEquityDailyPrice row) {
        fill(target, row);
    }

    private void fill(MarketPriceHistory e, BistEquityDailyPrice row) {
        BigDecimal mid = midPrice(row);
        e.setSymbol(row.symbol().toUpperCase());
        e.setBuyPrice(com.nurseli.marketdata.application.SpreadCalculator.buyPrice(mid));
        e.setSellPrice(com.nurseli.marketdata.application.SpreadCalculator.sellPrice(mid));
        e.setSource(resolveSource(row));
        e.setTimestamp(toHistoryTimestamp(row));
        e.setAdjustedClose(row.adjustedClose());
        e.setAdjustedAverage(row.adjustedAverage());
        e.setAdjustedLow(row.adjustedLow());
        e.setAdjustedHigh(row.adjustedHigh());
        e.setAdjustedVolume(row.adjustedVolume());
        e.setRawClose(row.rawClose());
        e.setRawAverage(row.rawAverage());
        e.setRawLow(row.rawLow());
        e.setRawHigh(row.rawHigh());
        e.setRawVolume(row.rawVolume());
        e.setUsdTry(row.usdTry());
        e.setBist100Value(row.bist100Value());
        e.setUsdPrice(row.usdPrice());
        e.setIndexBasedPrice(row.indexBasedPrice());
        e.setUsdVolume(row.usdVolume());
        e.setCapital(row.capital());
        e.setMarketCapTry(row.marketCapTry());
        e.setMarketCapUsd(row.marketCapUsd());
        e.setFreeFloatMarketCapTry(row.freeFloatMarketCapTry());
        e.setFreeFloatMarketCapUsd(row.freeFloatMarketCapUsd());
        e.setDollarBasedLow(row.dollarBasedLow());
        e.setDollarBasedHigh(row.dollarBasedHigh());
        e.setDollarBasedAverage(row.dollarBasedAverage());
        e.setDataQuality(row.dataQuality() != null ? row.dataQuality().name() : null);
        e.setCurrency("TRY");
    }

    private static BigDecimal midPrice(BistEquityDailyPrice row) {
        if (row.adjustedClose() != null) {
            return row.adjustedClose();
        }
        if (row.rawClose() != null) {
            return row.rawClose();
        }
        if (row.adjustedAverage() != null) {
            return row.adjustedAverage();
        }
        return BigDecimal.ZERO;
    }
}
