package com.nurseli.nrsfinanceportal.application.market;

import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsdOunceMetalTrySeriesTest {

    @Test
    void multipliesMetalMidByUsdTryAtSameTimestamp() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 1, 2, 12, 0);
        List<MarketPriceHistoryDto> metal =
                List.of(
                        new MarketPriceHistoryDto(new BigDecimal("100"), new BigDecimal("100"), t1),
                        new MarketPriceHistoryDto(new BigDecimal("110"), new BigDecimal("110"), t2));
        List<MarketPriceHistoryDto> fx =
                List.of(
                        new MarketPriceHistoryDto(new BigDecimal("10"), new BigDecimal("10"), t1),
                        new MarketPriceHistoryDto(new BigDecimal("20"), new BigDecimal("20"), t2));

        List<BigDecimal> tryCloses = UsdOunceMetalTrySeries.midClosesInTry(metal, fx, BigDecimal.ONE);

        assertEquals(2, tryCloses.size());
        assertEquals(0, new BigDecimal("1000").compareTo(tryCloses.get(0)));
        assertEquals(0, new BigDecimal("2200").compareTo(tryCloses.get(1)));
    }

    @Test
    void usesEarlierUsdTryWhenMetalTimestampIsAfterLastFxPoint() {
        LocalDateTime tMetal = LocalDateTime.of(2026, 5, 10, 12, 0);
        LocalDateTime tFx = LocalDateTime.of(2026, 5, 1, 12, 0);
        List<MarketPriceHistoryDto> metal =
                List.of(new MarketPriceHistoryDto(new BigDecimal("50"), new BigDecimal("50"), tMetal));
        List<MarketPriceHistoryDto> fx =
                List.of(new MarketPriceHistoryDto(new BigDecimal("30"), new BigDecimal("30"), tFx));

        List<BigDecimal> tryCloses = UsdOunceMetalTrySeries.midClosesInTry(metal, fx, BigDecimal.ONE);

        assertEquals(1, tryCloses.size());
        assertEquals(0, new BigDecimal("1500").compareTo(tryCloses.getFirst()));
    }

    @Test
    void detectsUsdOunceSymbols() {
        assertTrue(UsdOunceMetalTrySeries.isUsdPerOunceMetalSymbol("XAU_USD_OZ"));
    }
}
