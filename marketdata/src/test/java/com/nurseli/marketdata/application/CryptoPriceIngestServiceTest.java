package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.crypto.CryptoDailyOhlcUtil;
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
        when(coinGeckoClient.fetchDailyOhlc(eq("cardano"), any(Integer.class)))
                .thenReturn(List.of(
                        ohlcPoint(LocalDate.of(2024, 1, 1), "0.48", "0.52", "0.47", "0.50"),
                        ohlcPoint(LocalDate.of(2024, 1, 2), "0.53", "0.56", "0.51", "0.55")
                ));
        when(cryptoDailyCandleRepository.findBySymbolAndAsOf(eq("ADAUSDT"), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        service.ensureHistoryCoverage("ADAUSDT", from, to);

        verify(coinGeckoClient).fetchDailyOhlc(eq("cardano"), any(Integer.class));
        verify(coinGeckoClient, never()).fetchDailyMarketChart(eq("cardano"), any(Integer.class));
        ArgumentCaptor<CryptoDailyCandle> captor = ArgumentCaptor.forClass(CryptoDailyCandle.class);
        verify(cryptoDailyCandleRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CryptoDailyCandle::getAsOf)
                .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));
        assertThat(captor.getAllValues().get(0).getOpenPrice()).isEqualByComparingTo("0.48");
        assertThat(captor.getAllValues().get(0).getClosePrice()).isEqualByComparingTo("0.50");
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

        verify(coinGeckoClient, never()).fetchDailyOhlc(eq("cardano"), any(Integer.class));
        verify(coinGeckoClient, never()).fetchDailyMarketChart(eq("cardano"), any(Integer.class));
        verify(cryptoDailyCandleRepository, never()).save(any(CryptoDailyCandle.class));
    }

    @Test
    void upsertDailyOhlcPointsUpdatesFlatExistingCandle() {
        LocalDate day = LocalDate.of(2026, 5, 20);
        CryptoDailyCandle existing = new CryptoDailyCandle();
        existing.setSymbol("ETHUSDT");
        existing.setAsOf(day);
        existing.setOpenPrice(new BigDecimal("2000"));
        existing.setHighPrice(new BigDecimal("2000"));
        existing.setLowPrice(new BigDecimal("2000"));
        existing.setClosePrice(new BigDecimal("2000"));
        existing.setSource("COINGECKO_RANGE");

        when(cryptoDailyCandleRepository.findBySymbolAndAsOf("ETHUSDT", day))
                .thenReturn(Optional.of(existing));

        int changed = service.upsertDailyOhlcPoints(
                "ETHUSDT",
                List.of(ohlcPoint(day, "1990", "2015", "1985", "2005")),
                day,
                "COINGECKO_OHLC"
        );

        assertThat(changed).isEqualTo(1);
        assertThat(existing.getOpenPrice()).isEqualByComparingTo("1990");
        assertThat(existing.getHighPrice()).isEqualByComparingTo("2015");
        assertThat(existing.getLowPrice()).isEqualByComparingTo("1985");
        assertThat(existing.getClosePrice()).isEqualByComparingTo("2005");
        assertThat(existing.getSource()).isEqualTo("COINGECKO_OHLC");
        verify(cryptoDailyCandleRepository).save(existing);
    }

    @Test
    void upsertDailyOhlcPointsDoesNotOverwriteNonFlatExistingCandle() {
        LocalDate day = LocalDate.of(2026, 5, 20);
        CryptoDailyCandle existing = new CryptoDailyCandle();
        existing.setSymbol("ETHUSDT");
        existing.setAsOf(day);
        existing.setOpenPrice(new BigDecimal("1990"));
        existing.setHighPrice(new BigDecimal("2015"));
        existing.setLowPrice(new BigDecimal("1985"));
        existing.setClosePrice(new BigDecimal("2005"));
        existing.setSource("COINGECKO_OHLC");

        when(cryptoDailyCandleRepository.findBySymbolAndAsOf("ETHUSDT", day))
                .thenReturn(Optional.of(existing));

        int changed = service.upsertDailyOhlcPoints(
                "ETHUSDT",
                List.of(ohlcPoint(day, "2100", "2110", "2090", "2105")),
                day,
                "COINGECKO_OHLC"
        );

        assertThat(changed).isZero();
        assertThat(existing.getOpenPrice()).isEqualByComparingTo("1990");
        verify(cryptoDailyCandleRepository, never()).save(existing);
    }

    @Test
    void aggregateToDailyCombinesIntradayPointsIntoDailyOpenClose() {
        LocalDate day = LocalDate.of(2026, 5, 20);
        List<CoinGeckoClient.OhlcPoint> aggregated = CryptoDailyOhlcUtil.aggregateToDaily(List.of(
                ohlcPoint(day, "100", "105", "99", "102"),
                ohlcPoint(day, "102", "108", "101", "107")
        ));

        assertThat(aggregated).hasSize(1);
        assertThat(aggregated.get(0).open()).isEqualByComparingTo("100");
        assertThat(aggregated.get(0).high()).isEqualByComparingTo("108");
        assertThat(aggregated.get(0).low()).isEqualByComparingTo("99");
        assertThat(aggregated.get(0).close()).isEqualByComparingTo("107");
        assertThat(CryptoDailyOhlcUtil.isFlat(aggregated.get(0))).isFalse();
    }

    private static CoinGeckoClient.OhlcPoint ohlcPoint(
            LocalDate day,
            String open,
            String high,
            String low,
            String close
    ) {
        return new CoinGeckoClient.OhlcPoint(
                day,
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                null
        );
    }
}
