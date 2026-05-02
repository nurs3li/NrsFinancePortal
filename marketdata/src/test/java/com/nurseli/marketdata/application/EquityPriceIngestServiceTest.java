package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.EquityProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.finhub.FinHubCandleDto;
import com.nurseli.marketdata.infrastructure.finhub.FinHubClient;
import com.nurseli.marketdata.infrastructure.stooq.StooqCsvClient;
import com.nurseli.marketdata.infrastructure.yahoo.YahooChartClient;
import com.nurseli.marketdata.repository.EquityDailyCandleRepository;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EquityPriceIngestServiceTest {
    private FinHubClient finHubClient;
    private StooqCsvClient stooqCsvClient;
    private YahooChartClient yahooChartClient;
    private MarketPriceHistoryRepository repository;
    private EquityDailyCandleRepository equityDailyCandleRepository;
    private EquityProperties equityProperties;
    private EquityPriceIngestService service;

    @BeforeEach
    void setUp() {
        finHubClient = mock(FinHubClient.class);
        stooqCsvClient = mock(StooqCsvClient.class);
        yahooChartClient = mock(YahooChartClient.class);
        repository = mock(MarketPriceHistoryRepository.class);
        equityDailyCandleRepository = mock(EquityDailyCandleRepository.class);
        equityProperties = new EquityProperties();
        equityProperties.setSymbols(List.of("AAPL"));
        when(equityDailyCandleRepository.existsBySymbolAndAsOf(anyString(), any(LocalDate.class))).thenReturn(false);
        service = new EquityPriceIngestService(
                finHubClient,
                stooqCsvClient,
                yahooChartClient,
                repository,
                equityDailyCandleRepository,
                equityProperties
        );
    }

    @Test
    void backfill_inserts_from_finnhub_candles() {
        FinHubCandleDto dto = new FinHubCandleDto();
        dto.setS("ok");
        dto.setT(List.of(1714348800L, 1714435200L));
        dto.setC(List.of(100.0, 102.0));
        dto.setO(List.of(99.0, 101.0));
        dto.setH(List.of(101.0, 103.0));
        dto.setL(List.of(98.0, 100.0));
        when(finHubClient.fetchDailyCandles(anyString(), anyLong(), anyLong())).thenReturn(Mono.just(dto));
        when(repository.existsBySymbolAndTimestamp(anyString(), any(LocalDateTime.class))).thenReturn(false);

        service.fetchAndSaveHistoryBackfill(365, 10);

        ArgumentCaptor<MarketPriceHistory> captor = ArgumentCaptor.forClass(MarketPriceHistory.class);
        verify(repository, atLeast(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(x -> "FINHUB_HISTORY".equals(x.getSource())));
    }

    @Test
    void backfill_falls_back_to_stooq_when_finnhub_missing() {
        when(finHubClient.fetchDailyCandles(anyString(), anyLong(), anyLong())).thenReturn(Mono.empty());
        when(stooqCsvClient.fetchDailyRows(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(new StooqCsvClient.StooqDailyRow(
                        LocalDate.of(2026, 1, 2),
                        new BigDecimal("120.00"),
                        new BigDecimal("125.00"),
                        new BigDecimal("119.00"),
                        new BigDecimal("124.50"),
                        "1000"
                )));
        when(yahooChartClient.fetchDailyBars(anyString())).thenReturn(List.of());
        when(repository.existsBySymbolAndTimestamp(anyString(), any(LocalDateTime.class))).thenReturn(false);

        service.fetchAndSaveHistoryBackfill(365, 10);

        ArgumentCaptor<MarketPriceHistory> captor = ArgumentCaptor.forClass(MarketPriceHistory.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertEquals("STOOQ_FALLBACK", captor.getValue().getSource());
    }

    @Test
    void incremental_skips_when_data_already_exists_for_day() {
        FinHubCandleDto dto = new FinHubCandleDto();
        dto.setS("ok");
        dto.setT(List.of(1714348800L));
        dto.setC(List.of(100.0));
        dto.setO(List.of(99.0));
        dto.setH(List.of(101.0));
        dto.setL(List.of(98.0));
        when(finHubClient.fetchDailyCandles(anyString(), anyLong(), anyLong())).thenReturn(Mono.just(dto));
        when(yahooChartClient.fetchDailyBars(anyString())).thenReturn(List.of());
        MarketPriceHistory last = new MarketPriceHistory();
        last.setSymbol("AAPL");
        last.setTimestamp(LocalDateTime.now().minusDays(1));
        when(repository.findTopBySymbolOrderByTimestampDesc("AAPL"))
                .thenReturn(Optional.of(last));
        when(repository.existsBySymbolAndTimestamp(anyString(), any(LocalDateTime.class))).thenReturn(true);

        service.fetchAndSaveIncrementalDailyHistory();

        verify(repository, never()).save(any(MarketPriceHistory.class));
    }

    @Test
    void backfill_falls_back_to_yahoo_when_finnhub_and_stooq_missing() {
        when(finHubClient.fetchDailyCandles(anyString(), anyLong(), anyLong())).thenReturn(Mono.empty());
        when(stooqCsvClient.fetchDailyRows(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(yahooChartClient.fetchDailyBars(anyString()))
                .thenReturn(List.of(
                        new YahooChartClient.YahooDailyBar(
                                LocalDate.now().minusDays(2),
                                new BigDecimal("199.10"),
                                new BigDecimal("202.00"),
                                new BigDecimal("198.30"),
                                new BigDecimal("200.15"),
                                new BigDecimal("123000")
                        ),
                        new YahooChartClient.YahooDailyBar(
                                LocalDate.now().minusDays(1),
                                new BigDecimal("200.20"),
                                new BigDecimal("202.80"),
                                new BigDecimal("199.70"),
                                new BigDecimal("201.20"),
                                new BigDecimal("99000")
                        )
                ));
        when(repository.existsBySymbolAndTimestamp(anyString(), any(LocalDateTime.class))).thenReturn(false);

        service.fetchAndSaveHistoryBackfill(365, 10);

        ArgumentCaptor<MarketPriceHistory> captor = ArgumentCaptor.forClass(MarketPriceHistory.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertEquals("YAHOO_FALLBACK", captor.getValue().getSource());
    }
}
