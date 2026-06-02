package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.ingest.MetalPriceIngestService;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.coingecko.CoinGeckoMetalClient;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetalPriceIngestServiceTest {

    @Mock
    private CoinGeckoMetalClient client;
    @Mock
    private MarketPriceHistoryRepository repository;

    @InjectMocks
    private MetalPriceIngestService service;

    @Test
    void backfillRange_savesMissingGramGoldDays() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 2);
        when(client.fetchGoldTryHistoryPerOunceRange(from, to)).thenReturn(List.of(
                new CoinGeckoMetalClient.DailyGoldTryPoint(from, new BigDecimal("90000")),
                new CoinGeckoMetalClient.DailyGoldTryPoint(to, new BigDecimal("93000"))));
        when(repository.existsForDay(eq("XAU_TRY"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);

        MetalPriceIngestService.IngestSummary summary = service.backfillRange(from, to, "test");

        assertThat(summary.insertedRows()).isEqualTo(2);
        ArgumentCaptor<MarketPriceHistory> captor = ArgumentCaptor.forClass(MarketPriceHistory.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(MarketPriceHistory::getSymbol)
                .containsOnly("XAU_TRY");
        assertThat(captor.getAllValues())
                .extracting(MarketPriceHistory::getSource)
                .containsOnly("COINGECKO_RANGE");
    }
}
