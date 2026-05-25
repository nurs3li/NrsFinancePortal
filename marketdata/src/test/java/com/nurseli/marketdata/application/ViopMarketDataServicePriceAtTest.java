package com.nurseli.marketdata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.api.dto.ViopPriceAtResponse;
import com.nurseli.marketdata.config.MarketViopProperties;
import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import com.nurseli.marketdata.domain.viop.ViopPriceHistoryEntity;
import com.nurseli.marketdata.infrastructure.persistence.DerivativeSnapshotRepository;
import com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopClient;
import com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopHistoricalParser;
import com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopSnapshotParser;
import com.nurseli.marketdata.infrastructure.persistence.ViopPriceHistoryRepository;
import com.nurseli.marketdata.infrastructure.persistence.ViopSnapshotRepository;
import com.nurseli.marketdata.domain.viop.ViopPriceMatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViopMarketDataServicePriceAtTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private IsYatirimViopClient client;

    @Mock
    private ViopPriceHistoryRepository historyRepository;

    @Mock
    private ViopSnapshotRepository snapshotRepository;

    @Mock
    private ViopQueryService viopQueryService;

    @Mock
    private DerivativeSnapshotRepository derivativeSnapshotRepository;

    private ViopMarketDataService service;

    @BeforeEach
    void setUp() {
        MarketViopProperties props = new MarketViopProperties();
        props.setEnabled(true);
        props.setTimezone("Europe/Istanbul");
        MarketViopProperties.IndexEntry e = new MarketViopProperties.IndexEntry();
        e.setContractCode("F_USDTRY1226");
        e.setUnderlying("USDTRY");
        e.setDisplayName("USD/TRY");
        e.setContractName("USDTRY Vadeli");
        e.setMaturityMonth(12);
        e.setMaturityYear(2026);
        e.setAssetClass("FX");
        e.setSegment("FX_TRY_FUTURES");
        e.setChartType("PRICE_SERIES");
        e.setEnabled(true);
        props.getWhitelist().getFx().add(e);
        MarketViopProperties.IndexEntry eq = new MarketViopProperties.IndexEntry();
        eq.setContractCode("F_SISE0726");
        eq.setUnderlying("SISE");
        eq.setDisplayName("SISE Pay Vadeli");
        eq.setContractName("SISE Temmuz 2026 Vadeli");
        eq.setMaturityMonth(7);
        eq.setMaturityYear(2026);
        eq.setAssetClass("EQUITY");
        eq.setSegment("EQUITY_FUTURES");
        eq.setChartType("PRICE_SERIES");
        eq.setEnabled(true);
        props.getWhitelist().getEquity().add(eq);

        service = new ViopMarketDataService(
                props,
                redis,
                client,
                new IsYatirimViopHistoricalParser(new ObjectMapper()),
                new IsYatirimViopSnapshotParser(new ObjectMapper()),
                historyRepository,
                snapshotRepository,
                derivativeSnapshotRepository,
                viopQueryService);
    }

    @Test
    void priceAtExactDayUsesLastPointOnThatDay() {
        LocalDate day = LocalDate.of(2026, 5, 10);
        LocalDateTime t1 = LocalDateTime.of(2026, 5, 10, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 5, 10, 17, 0);
        ViopPriceHistoryEntity a = row(t1, "55.0");
        ViopPriceHistoryEntity b = row(t2, "55.82");
        when(historyRepository.findByContractCodeAndPriceTimeGreaterThanEqualAndPriceTimeLessThanOrderByPriceTimeAsc(
                        anyString(), any(), any()))
                .thenReturn(List.of(a, b));

        ViopPriceAtResponse r = service.getPriceAt("F_USDTRY1226", day);
        assertThat(r.matchType()).isEqualTo(ViopPriceMatchType.EXACT.name());
        assertThat(r.matchedPriceTime()).isEqualTo(t2);
        assertThat(r.price()).isEqualByComparingTo(new BigDecimal("55.82"));
    }

    @Test
    void priceAtDateTimeUsesLatestBarOnOrBeforeRequestedInstant() {
        LocalDateTime at = LocalDateTime.of(2026, 5, 10, 12, 0);
        ViopPriceHistoryEntity bar = row(LocalDateTime.of(2026, 5, 10, 11, 0), "55.5");
        when(historyRepository.findTopByContractCodeAndPriceTimeLessThanEqualOrderByPriceTimeDesc(anyString(), any()))
                .thenReturn(Optional.of(bar));

        ViopPriceAtResponse r = service.getPriceAt("F_USDTRY1226", at);
        assertThat(r.matchType()).isEqualTo(ViopPriceMatchType.PREVIOUS_AVAILABLE.name());
        assertThat(r.price()).isEqualByComparingTo(new BigDecimal("55.5"));
        assertThat(r.requestedDate()).isEqualTo(at);
        assertThat(r.matchedPriceTime()).isEqualTo(bar.getPriceTime());
    }

    @Test
    void priceAtPicksMorningBarOnRequestedDay() {
        LocalDate day = LocalDate.of(2026, 4, 29);
        ViopPriceHistoryEntity morning = row(LocalDateTime.of(2026, 4, 29, 10, 0), "55.94");
        when(historyRepository.findByContractCodeAndPriceTimeGreaterThanEqualAndPriceTimeLessThanOrderByPriceTimeAsc(
                        anyString(), any(), any()))
                .thenReturn(List.of(morning));

        ViopPriceAtResponse r = service.getPriceAt("USDTRY1226", day);
        assertThat(r.matchType()).isEqualTo(ViopPriceMatchType.EXACT.name());
        assertThat(r.matchedPriceTime()).isEqualTo(morning.getPriceTime());
        assertThat(r.price()).isEqualByComparingTo(new BigDecimal("55.94"));
    }

    @Test
    void priceAtPreviousWhenNoDataOnDay() {
        LocalDate day = LocalDate.of(2026, 5, 10);
        when(historyRepository.findByContractCodeAndPriceTimeGreaterThanEqualAndPriceTimeLessThanOrderByPriceTimeAsc(
                        anyString(), any(), any()))
                .thenReturn(List.of());
        ViopPriceHistoryEntity prev = row(LocalDateTime.of(2026, 5, 8, 17, 0), "55.1");
        when(historyRepository.findTopByContractCodeAndPriceTimeLessThanOrderByPriceTimeDesc(
                        anyString(), any()))
                .thenReturn(Optional.of(prev));

        ViopPriceAtResponse r = service.getPriceAt("F_USDTRY1226", day);
        assertThat(r.matchType()).isEqualTo(ViopPriceMatchType.PREVIOUS_AVAILABLE.name());
        assertThat(r.price()).isEqualByComparingTo(new BigDecimal("55.1"));
    }

    @Test
    void priceAtNotFound() {
        LocalDate day = LocalDate.of(2026, 5, 10);
        when(historyRepository.findByContractCodeAndPriceTimeGreaterThanEqualAndPriceTimeLessThanOrderByPriceTimeAsc(
                        anyString(), any(), any()))
                .thenReturn(List.of());
        when(historyRepository.findTopByContractCodeAndPriceTimeLessThanOrderByPriceTimeDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        when(derivativeSnapshotRepository.findByContractCodeInAndAsOfSince(any(), any()))
                .thenReturn(List.of());

        ViopPriceAtResponse r = service.getPriceAt("F_USDTRY1226", day);
        assertThat(r.matchType()).isEqualTo(ViopPriceMatchType.NOT_FOUND.name());
        assertThat(r.price()).isNull();
    }

    @Test
    void priceAtFallsBackToDerivativeSnapshotSeries() {
        LocalDate day = LocalDate.of(2026, 3, 4);
        when(historyRepository.findByContractCodeAndPriceTimeGreaterThanEqualAndPriceTimeLessThanOrderByPriceTimeAsc(
                        anyString(), any(), any()))
                .thenReturn(List.of());
        when(historyRepository.findTopByContractCodeAndPriceTimeLessThanOrderByPriceTimeDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        DerivativeSnapshot snap = new DerivativeSnapshot();
        snap.setContractCode("SISE0726");
        snap.setAsOf(LocalDateTime.of(2026, 3, 4, 18, 0));
        snap.setPrice(new BigDecimal("55.45"));
        when(derivativeSnapshotRepository.findByContractCodeInAndAsOfSince(any(Set.class), any()))
                .thenReturn(List.of(snap));

        ViopPriceAtResponse r = service.getPriceAt("SISE0726", day);
        assertThat(r.matchType()).isEqualTo(ViopPriceMatchType.EXACT.name());
        assertThat(r.price()).isEqualByComparingTo(new BigDecimal("55.45"));
        assertThat(r.source()).isEqualTo("VIOP_SNAPSHOT_SERIES");
    }

    @Test
    void priceAtBackfillKeepsPrefixedContractCodeForProviderHistory() {
        LocalDate day = LocalDate.now(ZoneId.of("Europe/Istanbul")).minusDays(1);
        ViopPriceHistoryEntity fetched = row("F_SISE0726", day.atTime(17, 0), "54.11");
        when(historyRepository.findByContractCodeAndPriceTimeGreaterThanEqualAndPriceTimeLessThanOrderByPriceTimeAsc(
                        anyString(), any(), any()))
                .thenReturn(List.of(), List.of(), List.of(), List.of(), List.of(fetched));
        when(historyRepository.findTopByContractCodeAndPriceTimeLessThanOrderByPriceTimeDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        when(client.fetchHistorical(eq("F_SISE0726"), any(), any(), eq(60)))
                .thenReturn("{\"data\":[[1779206400000,54.11]],\"timestamp\":\"2026-05-25T20:41:03.2714219+03:00\"}");
        when(historyRepository.insertIgnore(anyString(), anyString(), anyString(), anyString(), any(), any(), anyInt(), anyString(), any()))
                .thenReturn(1);

        ViopPriceAtResponse r = service.getPriceAt("SISE0726", day);

        assertThat(r.matchType()).isEqualTo(ViopPriceMatchType.EXACT.name());
        assertThat(r.price()).isEqualByComparingTo(new BigDecimal("54.11"));
        verify(client).fetchHistorical(eq("F_SISE0726"), any(), any(), eq(60));
    }

    private static ViopPriceHistoryEntity row(LocalDateTime t, String price) {
        return row("F_USDTRY1226", t, price);
    }

    private static ViopPriceHistoryEntity row(String contractCode, LocalDateTime t, String price) {
        ViopPriceHistoryEntity e = new ViopPriceHistoryEntity();
        e.setContractCode(contractCode);
        e.setUnderlying("USDTRY");
        e.setAssetClass("FX");
        e.setSegment("FX_TRY_FUTURES");
        e.setPriceTime(t);
        e.setPrice(new BigDecimal(price));
        e.setPeriodMinutes(60);
        e.setSource("IS_YATIRIM");
        return e;
    }
}
