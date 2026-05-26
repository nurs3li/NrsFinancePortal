package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.CryptoHistoryBackfillProperties;
import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import com.nurseli.marketdata.infrastructure.coingecko.CoinGeckoClient;
import com.nurseli.marketdata.infrastructure.persistence.CryptoDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoPriceIngestServiceTest {

    @Mock
    private CoinGeckoClient coinGeckoClient;
    @Mock
    private MarketPriceHistoryRepository marketPriceHistoryRepository;
    @Mock
    private CryptoDailyCandleRepository cryptoDailyCandleRepository;

    private CryptoPriceIngestService service;

    @BeforeEach
    void setUp() {
        service = new CryptoPriceIngestService(
                coinGeckoClient,
                marketPriceHistoryRepository,
                cryptoDailyCandleRepository,
                new CryptoHistoryBackfillProperties()
        );
    }

    @Test
    void ensureHistoryCoverageBackfillsMissingOlderCandles() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 10);

        CryptoDailyCandle oldest = new CryptoDailyCandle();
        oldest.setSymbol("ADAUSDT");
        oldest.setAsOf(LocalDate.of(2026, 5, 9));
        when(cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfAsc("ADAUSDT"))
                .thenReturn(Optional.of(oldest));
        when(coinGeckoClient.fetchDailyMarketChart(eq("cardano"), any(Integer.class)))
                .thenReturn(List.of(
                        new CoinGeckoClient.OhlcPoint(
                                LocalDate.of(2024, 1, 1),
                                new BigDecimal("0.50"),
                                new BigDecimal("0.50"),
                                new BigDecimal("0.50"),
                                new BigDecimal("0.50"),
                                null
                        ),
                        new CoinGeckoClient.OhlcPoint(
                                LocalDate.of(2024, 1, 2),
                                new BigDecimal("0.55"),
                                new BigDecimal("0.55"),
                                new BigDecimal("0.55"),
                                new BigDecimal("0.55"),
                                null
                        )
                ));
        when(cryptoDailyCandleRepository.existsBySymbolAndAsOf(eq("ADAUSDT"), any(LocalDate.class)))
                .thenReturn(false);

        service.ensureHistoryCoverage("ADAUSDT", from, to);

        ArgumentCaptor<CryptoDailyCandle> captor = ArgumentCaptor.forClass(CryptoDailyCandle.class);
        verify(cryptoDailyCandleRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CryptoDailyCandle::getAsOf)
                .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));
    }

    @Test
    void ensureHistoryCoverageSkipsWhenOldestAlreadyCoversRequestedDate() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 10);

        CryptoDailyCandle oldest = new CryptoDailyCandle();
        oldest.setSymbol("ADAUSDT");
        oldest.setAsOf(LocalDate.of(2023, 12, 25));
        when(cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfAsc("ADAUSDT"))
                .thenReturn(Optional.of(oldest));

        service.ensureHistoryCoverage("ADAUSDT", from, to);

        verify(coinGeckoClient, never()).fetchDailyMarketChart(eq("cardano"), any(Integer.class));
        verify(cryptoDailyCandleRepository, never()).save(any(CryptoDailyCandle.class));
    }
}
