package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.ingest.FundPriceIngestService;
import com.nurseli.marketdata.config.EtfProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.finhub.FinHubClient;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import com.nurseli.marketdata.infrastructure.yahoo.YahooChartClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundPriceIngestServiceTest {

    @Mock
    private FinHubClient finHubClient;
    @Mock
    private YahooChartClient yahooChartClient;
    @Mock
    private MarketPriceHistoryRepository repository;

    private FundPriceIngestService service;

    @BeforeEach
    void setUp() {
        EtfProperties properties = new EtfProperties();
        properties.setHistoryUseYahooOnly(true);
        properties.setHistorySkipIfFreshWithinDays(3);
        properties.setSymbols(List.of("EEM"));
        service = new FundPriceIngestService(finHubClient, yahooChartClient, repository, properties);
    }

    @Test
    void ingestHistoryIfNeeded_backfillsWhenLatestIsFreshButCoverageIsSparse() {
        MarketPriceHistory oldest = row("EEM", LocalDate.now().minusDays(3));
        MarketPriceHistory latest = row("EEM", LocalDate.now().minusDays(1));
        when(repository.findTopBySymbolOrderByTimestampDesc("EEM")).thenReturn(Optional.of(latest));
        when(repository.findTopBySymbolOrderByTimestampAsc("EEM")).thenReturn(Optional.of(oldest));
        when(yahooChartClient.fetchDailyBars("EEM", "1y")).thenReturn(List.of());

        service.ingestHistoryIfNeeded("EEM", 365);

        verify(yahooChartClient).fetchDailyBars("EEM", "1y");
    }

    @Test
    void ingestHistoryIfNeeded_skipsWhenCoverageIsFreshAndSufficient() {
        LocalDate now = LocalDate.now();
        MarketPriceHistory oldest = row("EEM", now.minusDays(365));
        MarketPriceHistory latest = row("EEM", now.minusDays(1));
        when(repository.findTopBySymbolOrderByTimestampDesc("EEM")).thenReturn(Optional.of(latest));
        when(repository.findTopBySymbolOrderByTimestampAsc("EEM")).thenReturn(Optional.of(oldest));
        when(repository.countBySymbolAndTimestampRange(eq("EEM"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(260L);

        service.ingestHistoryIfNeeded("EEM", 365);

        verify(yahooChartClient, never()).fetchDailyBars(eq("EEM"), anyString());
    }

    private static MarketPriceHistory row(String symbol, LocalDate day) {
        MarketPriceHistory row = new MarketPriceHistory();
        row.setSymbol(symbol);
        row.setBuyPrice(new BigDecimal("10"));
        row.setSellPrice(new BigDecimal("10"));
        row.setTimestamp(day.atTime(12, 0));
        return row;
    }
}
