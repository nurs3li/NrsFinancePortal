package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.PreciousMetalUsdChanges;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreciousMetalUsdChangeCalculatorTest {

    @Mock
    private MarketPriceHistoryRepository repository;

    @InjectMocks
    private PreciousMetalUsdChangeCalculator calculator;

    private static MarketPriceHistory row(LocalDateTime ts, BigDecimal px) {
        MarketPriceHistory h = new MarketPriceHistory();
        h.setTimestamp(ts);
        h.setBuyPrice(px);
        h.setSellPrice(px);
        return h;
    }

    @Test
    void computesDailyWeeklyMonthlyYearly() {
        LocalDateTime t0 = LocalDateTime.of(2024, 1, 2, 0, 0);
        LocalDateTime t1 = LocalDateTime.of(2024, 1, 3, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2024, 1, 10, 0, 0);
        LocalDateTime t3 = LocalDateTime.of(2024, 2, 3, 0, 0);
        LocalDateTime t4 = LocalDateTime.of(2025, 1, 3, 0, 0);
        List<MarketPriceHistory> asc = List.of(
                row(t0, new BigDecimal("100")),
                row(t1, new BigDecimal("110")),
                row(t2, new BigDecimal("120")),
                row(t3, new BigDecimal("130")),
                row(t4, new BigDecimal("200")));
        when(repository.findTopBySymbolAndSourceOrderByTimestampDesc("XAU_USD_OZ", PreciousMetalUsdCatalog.SOURCE))
                .thenReturn(Optional.of(row(t4, new BigDecimal("200"))));
        when(repository.findBySymbolAndSourceAndTimestampRange(
                eq("XAU_USD_OZ"), eq(PreciousMetalUsdCatalog.SOURCE), any(), any()))
                .thenReturn(asc);

        PreciousMetalUsdChanges c = calculator.compute("XAU_USD_OZ");
        assertThat(c.changeDailyPercent()).isEqualByComparingTo(new BigDecimal("53.85"));
        assertThat(c.changeWeeklyPercent()).isNotNull();
        assertThat(c.changeMonthlyPercent()).isNotNull();
        assertThat(c.changeYearlyPercent()).isNotNull();
    }

    @Test
    void returnsNullsWhenNoLatest() {
        when(repository.findTopBySymbolAndSourceOrderByTimestampDesc("XAG_USD_OZ", PreciousMetalUsdCatalog.SOURCE))
                .thenReturn(Optional.empty());
        PreciousMetalUsdChanges c = calculator.compute("XAG_USD_OZ");
        assertThat(c.changeDailyPercent()).isNull();
        assertThat(c.changeWeeklyPercent()).isNull();
    }
}
