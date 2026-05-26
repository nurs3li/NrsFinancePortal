package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.config.ManualPortfolioPriceResolveProperties;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceLatestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalManualPriceResolverServiceTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Mock
    private MarketDataClient marketDataClient;

    private HistoricalManualPriceResolverService service;

    private final ManualPortfolioPriceResolveProperties resolveProperties = new ManualPortfolioPriceResolveProperties();

    @BeforeEach
    void setUp() {
        resolveProperties.setMaxLookbackDays(7);
        service = new HistoricalManualPriceResolverService(marketDataClient, resolveProperties);
        when(marketDataClient.loadLatestPricing()).thenReturn(snapWithUsdTry(new BigDecimal("50")));
    }

    /**
     * Alış günü: VOO 10 USD × o gün USDTRY mid 32 = 320 TRY (spot 50 ile değil).
     */
    @Test
    void resolveFundUsesHistoricalUsdTryOnBuyDate() {
        LocalDate buyDate = LocalDate.of(2024, 6, 10);

        when(marketDataClient.getHistory(eq(AssetType.FUND), eq("VOO"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("10"), new BigDecimal("10"), buyDate.atStartOfDay())
                ));
        when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("30"), new BigDecimal("34"), buyDate.minusDays(1).atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("32"), new BigDecimal("32"), buyDate.atStartOfDay())
                ));

        var result = service.resolve(AssetType.FUND, "VOO", buyDate);

        assertThat(result.isFound()).isTrue();
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("320"));
    }

    @Test
    void dailySeriesUsesPerDayUsdTryNotSpot() {
        LocalDate d1 = LocalDate.of(2024, 6, 10);
        LocalDate d2 = d1.plusDays(3);

        when(marketDataClient.getHistory(eq(AssetType.STOCK), eq("TSLA"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("10"), new BigDecimal("10"), d1.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("12"), new BigDecimal("12"), d2.atStartOfDay())
                ));
        when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("32"), new BigDecimal("32"), d1.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("40"), new BigDecimal("40"), d2.atStartOfDay())
                ));

        List<HistoricalManualPriceResolverService.ManualChartPoint> series =
                service.loadDailyCloseSeriesTry(AssetType.STOCK, "TSLA", d1, d2);

        assertThat(series).hasSize(2);
        assertThat(series.get(0).priceTry()).isEqualByComparingTo(new BigDecimal("320"));
        assertThat(series.get(1).priceTry()).isEqualByComparingTo(new BigDecimal("480"));
    }

    @Test
    void bistSymbolSkipsUsdTryConversion() {
        LocalDate day = LocalDate.of(2024, 6, 10);
        when(marketDataClient.getBistBatchHistoryMapped(eq("AKBNK"), any(), any()))
                .thenReturn(Map.of(
                        "AKBNK",
                        List.of(new MarketPriceHistoryDto(new BigDecimal("55"), new BigDecimal("55"), day.atStartOfDay()))
                ));

        var result = service.resolve(AssetType.STOCK, "AKBNK.IS", day);

        assertThat(result.isFound()).isTrue();
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("55"));
        verify(marketDataClient).getBistBatchHistoryMapped(eq("AKBNK"), any(), any());
    }

    @Test
    void resolveUsesSameDayHourlyWhenDailyCloseMissing() {
        LocalDate day = todayIstanbul().minusDays(3);
        LocalDate previous = day.minusDays(1);

        when(marketDataClient.getChartCompatibleHistory(eq(AssetType.CRYPTO), eq("AVAXUSDT"), anyInt(), eq("daily")))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("9"), new BigDecimal("9"), previous.atStartOfDay())
                ));
        when(marketDataClient.getChartCompatibleHistory(eq(AssetType.CRYPTO), eq("AVAXUSDT"), anyInt(), eq("hourly")))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("11"), new BigDecimal("11"), day.atTime(10, 0)),
                        new MarketPriceHistoryDto(new BigDecimal("12"), new BigDecimal("12"), day.atTime(17, 0))
                ));
        when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("32"), new BigDecimal("32"), day.atStartOfDay())
                ));

        var result = service.resolve(AssetType.CRYPTO, "AVAXUSDT", day);

        assertThat(result.isFound()).isTrue();
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("384"));
        assertThat(result.getSource()).isEqualTo(ManualPriceSource.MARKET_HISTORY_SAME_DAY_HOURLY.name());
        assertThat(result.getResolvedDate()).isEqualTo(day);
    }

    @Test
    void resolveUsesNextCloseWhenPreviousCloseUnavailable() {
        LocalDate requested = LocalDate.of(2024, 6, 10);
        LocalDate next = requested.plusDays(2);

        when(marketDataClient.getChartCompatibleHistory(eq(AssetType.FX), eq("USDTRY"), anyInt(), eq("daily")))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("33"), new BigDecimal("33"), next.atStartOfDay())
                ));

        var result = service.resolve(AssetType.FX, "USDTRY", requested);

        assertThat(result.isFound()).isTrue();
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("33"));
        assertThat(result.getSource()).isEqualTo(ManualPriceSource.MARKET_HISTORY_NEXT_CLOSE.name());
        assertThat(result.getResolvedDate()).isEqualTo(next);
    }

    @Test
    void resolveUsdOunceMetalUsesHistoricalUsdTry() {
        LocalDate day = LocalDate.of(2024, 6, 10);

        when(marketDataClient.getChartCompatibleHistory(eq(AssetType.METAL), eq("XAU_USD_OZ"), anyInt(), eq("daily")))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("10"), new BigDecimal("10"), day.atStartOfDay())
                ));
        when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("32"), new BigDecimal("32"), day.atStartOfDay())
                ));

        var result = service.resolve(AssetType.METAL, "XAU_USD_OZ", day);

        assertThat(result.isFound()).isTrue();
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("320"));
    }

    private static LatestPricingSnapshot snapWithUsdTry(BigDecimal spot) {
        MarketPriceLatestDto usdTry = new MarketPriceLatestDto(
                "USDTRY",
                spot,
                spot,
                "TEST",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "EXACT",
                null,
                null,
                null
        );
        return new LatestPricingSnapshot(
                Map.of("USDTRY", usdTry),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    private static LocalDate todayIstanbul() {
        return LocalDate.now(ISTANBUL);
    }
}
