package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.SimulationResponseDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

class SimulationServiceTest {

    @Test
    void shouldUseExactWhenHistoryContainsBuyDate() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.now().minusDays(5);
        Mockito.when(marketDataClient.getHistory(eq(AssetType.CRYPTO), eq("BTCUSDT"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("100"), new BigDecimal("100"), buyDate.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("120"), new BigDecimal("120"), LocalDateTime.now().minusDays(1))
                ));
        Mockito.when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("30"), new BigDecimal("30"), buyDate.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("40"), new BigDecimal("40"), LocalDateTime.now().minusDays(1))
                ));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.CRYPTO), eq("BTCUSDT")))
                .thenReturn(new BigDecimal("125"));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.FX), eq("USDTRY")))
                .thenReturn(new BigDecimal("35"));

        SimulationResponseDto response = service.simulate(
                AssetType.CRYPTO, "BTCUSDT", new BigDecimal("1000"), buyDate, null
        );

        assertEquals("EXACT", response.getQualityFlag());
        assertEquals(SimulationService.NOTICE_USD_DENOMINATED, response.getApproximationNoticeCode());
    }

    @Test
    void shouldUsePreviousDayWhenExactMissing() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.now().minusDays(5);
        LocalDate previous = buyDate.minusDays(1);
        Mockito.when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("35"), new BigDecimal("35"), previous.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("36"), new BigDecimal("36"), LocalDateTime.now().minusDays(1))
                ));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.FX), eq("USDTRY")))
                .thenReturn(new BigDecimal("37"));

        SimulationResponseDto response = service.simulate(
                AssetType.FX, "USDTRY", new BigDecimal("1000"), buyDate, null
        );

        assertEquals("PREVIOUS_DAY", response.getQualityFlag());
        assertEquals(null, response.getApproximationNoticeCode());
    }

    @Test
    void shouldUseUserInputWhenManualPriceProvided() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.now().minusDays(3);
        Mockito.when(marketDataClient.getHistory(any(), any(), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("10"), new BigDecimal("10"), buyDate.atStartOfDay())
                ));
        Mockito.when(marketDataClient.getPriceTry(any(), any()))
                .thenReturn(new BigDecimal("11"));

        SimulationResponseDto response = service.simulate(
                AssetType.METAL, "XAU_TRY", new BigDecimal("5000"), buyDate, new BigDecimal("9.5")
        );

        assertEquals("USER_INPUT", response.getBuyPriceSource());
        assertEquals("EXACT", response.getQualityFlag());
        assertEquals(null, response.getApproximationNoticeCode());
    }

    /**
     * Geçmiş seri: iki günde farklı USDTRY; alım gününde BTC 10 USD ve kur 32 → TRY alış birimi 320 TRY.
     * Güncel fiyat TRY spot ile (ör. 12 USD × 50 = 600) verilir; geçmiş çarpan gün bazlı olmalıdır.
     */
    @Test
    void shouldNormalizeCryptoHistoryWithHistoricalUsdTryPerTimestamp() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.of(2024, 6, 10);
        LocalDate later = buyDate.plusDays(3);

        Mockito.when(marketDataClient.getHistory(eq(AssetType.CRYPTO), eq("BTCUSDT"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("10"), new BigDecimal("10"), buyDate.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("12"), new BigDecimal("12"), later.atStartOfDay())
                ));
        Mockito.when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("30"), new BigDecimal("34"), buyDate.minusDays(1).atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("32"), new BigDecimal("32"), buyDate.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("40"), new BigDecimal("40"), later.atStartOfDay())
                ));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.CRYPTO), eq("BTCUSDT")))
                .thenReturn(new BigDecimal("600"));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.FX), eq("USDTRY")))
                .thenReturn(new BigDecimal("50"));

        SimulationResponseDto response = service.simulate(
                AssetType.CRYPTO, "BTCUSDT", new BigDecimal("3200"), buyDate, null
        );

        // units = 3200 / (10 * 32) = 10; currentValue = 10 * 600 = 6000
        assertEquals(0, response.getHistoricalPriceTry().compareTo(new BigDecimal("320")));
        assertEquals(0, response.getCurrentValueTry().compareTo(new BigDecimal("6000.00")));
        assertEquals("EXACT", response.getQualityFlag());
    }

    /**
     * FON geçmişi USD birimindedir; STOCK/CRYPTO ile aynı şekilde mum zamanına göre tarihsel USDTRY ile TRY'ye çevrilir.
     */
    @Test
    void shouldNormalizeFundHistoryWithHistoricalUsdTryPerTimestamp() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.of(2024, 6, 10);
        LocalDate later = buyDate.plusDays(3);

        Mockito.when(marketDataClient.getHistory(eq(AssetType.FUND), eq("VOO"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("10"), new BigDecimal("10"), buyDate.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("12"), new BigDecimal("12"), later.atStartOfDay())
                ));
        Mockito.when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("30"), new BigDecimal("34"), buyDate.minusDays(1).atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("32"), new BigDecimal("32"), buyDate.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("40"), new BigDecimal("40"), later.atStartOfDay())
                ));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.FUND), eq("VOO")))
                .thenReturn(new BigDecimal("600"));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.FX), eq("USDTRY")))
                .thenReturn(new BigDecimal("50"));

        SimulationResponseDto response = service.simulate(
                AssetType.FUND, "VOO", new BigDecimal("3200"), buyDate, null
        );

        assertEquals(0, response.getHistoricalPriceTry().compareTo(new BigDecimal("320")));
        assertEquals(0, response.getCurrentValueTry().compareTo(new BigDecimal("6000.00")));
        assertEquals("EXACT", response.getQualityFlag());
        assertEquals(SimulationService.NOTICE_USD_DENOMINATED, response.getApproximationNoticeCode());
    }

    @Test
    void shouldRequireManualPriceWhenHistoryOnlyAfterBuyDate() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.of(2024, 1, 1);
        LocalDate firstData = buyDate.plusMonths(6);
        Mockito.when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("35"), new BigDecimal("35"), firstData.atStartOfDay())
                ));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.FX), eq("USDTRY")))
                .thenReturn(new BigDecimal("37"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.simulate(AssetType.FX, "USDTRY", new BigDecimal("1000"), buyDate, null)
        );
        assertTrue(ex.getMessage().contains(SimulationService.MANUAL_PRICE_REQUIRED));
    }

    @Test
    void shouldRequireManualPriceWhenHistoryEmpty() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.now().minusDays(10);
        Mockito.when(marketDataClient.getHistory(eq(AssetType.METAL), eq("XAU_TRY"), anyInt()))
                .thenReturn(List.of());
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.METAL), eq("XAU_TRY")))
                .thenReturn(new BigDecimal("2500"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.simulate(AssetType.METAL, "XAU_TRY", new BigDecimal("1000"), buyDate, null)
        );
        assertTrue(ex.getMessage().contains(SimulationService.MANUAL_PRICE_REQUIRED));
    }

    @Test
    void shouldAnchorChartAtBuyDateWhenFirstHistoryIsLater() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.of(2024, 1, 1);
        LocalDate firstData = LocalDate.of(2024, 3, 1);
        Mockito.when(marketDataClient.getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("30"), new BigDecimal("30"), buyDate.minusDays(5).atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("32"), new BigDecimal("32"), firstData.atStartOfDay())
                ));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.FX), eq("USDTRY")))
                .thenReturn(new BigDecimal("40"));

        SimulationResponseDto response = service.simulate(
                AssetType.FX, "USDTRY", new BigDecimal("1000"), buyDate, new BigDecimal("31")
        );

        assertEquals(buyDate, response.getPerformanceSeries().get(0).date());
        assertEquals(0, response.getPerformanceSeries().get(0).cumulativeReturnPct().compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldAllowBuyDateTodayWhenHistoryContainsToday() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.now();
        Mockito.when(marketDataClient.getHistory(eq(AssetType.FX), eq("GBPTRY"), anyInt()))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("44"), new BigDecimal("44"), buyDate.atStartOfDay())
                ));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.FX), eq("GBPTRY")))
                .thenReturn(new BigDecimal("45"));

        SimulationResponseDto response = service.simulate(
                AssetType.FX, "GBPTRY", new BigDecimal("5000"), buyDate, null
        );

        assertEquals("EXACT", response.getQualityFlag());
        assertEquals(0, response.getHistoricalPriceTry().compareTo(new BigDecimal("44")));
    }

    @Test
    void shouldUseLastHistoryWhenBistSpotMissing() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.of(2024, 6, 10);
        LocalDate later = buyDate.plusDays(2);
        Mockito.when(marketDataClient.getBistHistoryBetween(eq("AKBNK"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("50"), new BigDecimal("50"), buyDate.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("55"), new BigDecimal("55"), later.atStartOfDay())
                ));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.BIST), eq("AKBNK")))
                .thenReturn(BigDecimal.ZERO);

        SimulationResponseDto response = service.simulate(
                AssetType.BIST, "AKBNK", new BigDecimal("5000"), buyDate, null
        );

        assertEquals(0, response.getCurrentPriceTry().compareTo(new BigDecimal("55")));
        assertTrue(response.getPerformanceSeries().size() >= 2);
    }

    @Test
    void bistUsesTryHistoryWithoutUsdTryFxFetch() {
        MarketDataClient marketDataClient = Mockito.mock(MarketDataClient.class);
        DateToDaysHelper dateToDaysHelper = new DateToDaysHelper();
        SimulationService service = new SimulationService(marketDataClient, dateToDaysHelper);

        LocalDate buyDate = LocalDate.of(2024, 6, 10);
        Mockito.when(marketDataClient.getBistHistoryBetween(eq("THYAO"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        new MarketPriceHistoryDto(new BigDecimal("100"), new BigDecimal("100"), buyDate.atStartOfDay()),
                        new MarketPriceHistoryDto(new BigDecimal("110"), new BigDecimal("110"), buyDate.plusDays(2).atStartOfDay())
                ));
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.BIST), eq("THYAO")))
                .thenReturn(new BigDecimal("120"));

        SimulationResponseDto response = service.simulate(
                AssetType.BIST, "THYAO", new BigDecimal("5000"), buyDate, null
        );

        Mockito.verify(marketDataClient, never()).getHistory(eq(AssetType.FX), eq("USDTRY"), anyInt());
        assertEquals("EXACT", response.getQualityFlag());
        assertEquals(null, response.getApproximationNoticeCode());
        assertEquals(0, response.getHistoricalPriceTry().compareTo(new BigDecimal("100")));
    }
}
