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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

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
        Mockito.when(marketDataClient.getPriceTry(eq(AssetType.CRYPTO), eq("BTCUSDT")))
                .thenReturn(new BigDecimal("125"));

        SimulationResponseDto response = service.simulate(
                AssetType.CRYPTO, "BTCUSDT", new BigDecimal("1000"), buyDate, null
        );

        assertEquals("EXACT", response.getQualityFlag());
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
    }
}
