package com.nurseli.marketdata.application.bist;

import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.bist.BistDataQuality;
import com.nurseli.marketdata.infrastructure.bist.BistEquityDailyPrice;
import com.nurseli.marketdata.infrastructure.bist.BistEquityMarketProvider;
import com.nurseli.marketdata.infrastructure.bist.BistProviderError;
import com.nurseli.marketdata.infrastructure.bist.BistProviderResult;
import com.nurseli.marketdata.infrastructure.bist.BistProviderSource;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BistEquityIngestServiceTest {

    @Mock private BistEquityMarketProvider provider;
    @Mock private MarketPriceHistoryRepository repository;
    @Mock private BistSymbolCatalog catalog;
    @Mock private BistProperties bistProperties;

    private BistEquityPersistenceMapper mapper;
    private BistEquityIngestService service;

    @BeforeEach
    void setUp() {
        mapper = new BistEquityPersistenceMapper();
        lenient().when(bistProperties.getHistoryFetchChunkDays()).thenReturn(10_000);
        lenient().when(bistProperties.getDelayMsBetweenHistoryChunks()).thenReturn(0);
        service = new BistEquityIngestService(provider, repository, catalog, mapper, bistProperties);
    }

    private static BistEquityDailyPrice row(String sym, LocalDate d, BigDecimal close) {
        return new BistEquityDailyPrice(
                sym,
                d,
                close,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BistProviderSource.IS_YATIRIM,
                BistDataQuality.HISTORICAL,
                Instant.now());
    }

    @Test
    void unsupported_symbol_returns_without_save() {
        when(catalog.isSupported("ZZZZ")).thenReturn(false);
        BistEquityIngestSymbolResult r = service.ingestHistory("ZZZZ", LocalDate.now().minusDays(1), LocalDate.now());
        assertFalse(r.success());
        assertEquals(0, r.rowsWritten());
        verify(repository, never()).save(any());
    }

    @Test
    void provider_rows_persisted() {
        when(catalog.isSupported("THYAO")).thenReturn(true);
        LocalDate d = LocalDate.of(2025, 3, 1);
        when(provider.fetchHistory(eq("THYAO"), any(), any()))
                .thenReturn(BistProviderResult.success(List.of(row("THYAO", d, new BigDecimal("10")))));
        when(repository.findBySymbolAndSourceAndTimestamp(eq("THYAO"), eq("IS_YATIRIM"), any()))
                .thenReturn(Optional.empty());

        BistEquityIngestSymbolResult r = service.ingestHistory("THYAO", d, d);
        assertTrue(r.success());
        assertEquals(1, r.rowsFetched());
        assertEquals(1, r.rowsWritten());
        verify(repository).save(any(MarketPriceHistory.class));
    }

    @Test
    void second_ingest_updates_same_logical_row_no_extra_insert_strategy() {
        when(catalog.isSupported("THYAO")).thenReturn(true);
        LocalDate d = LocalDate.of(2025, 3, 2);
        when(provider.fetchHistory(eq("THYAO"), any(), any()))
                .thenReturn(BistProviderResult.success(List.of(row("THYAO", d, new BigDecimal("11")))));

        MarketPriceHistory existing = new MarketPriceHistory();
        existing.setId(5L);
        existing.setSymbol("THYAO");
        existing.setSource("IS_YATIRIM");
        existing.setTimestamp(d.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime());
        existing.setBuyPrice(BigDecimal.ONE);
        existing.setSellPrice(BigDecimal.ONE);

        when(repository.findBySymbolAndSourceAndTimestamp(eq("THYAO"), eq("IS_YATIRIM"), any()))
                .thenReturn(Optional.of(existing));

        service.ingestHistory("THYAO", d, d);
        ArgumentCaptor<MarketPriceHistory> cap = ArgumentCaptor.forClass(MarketPriceHistory.class);
        verify(repository, times(1)).save(cap.capture());
        assertEquals(5L, cap.getValue().getId());
        assertEquals(0, new BigDecimal("11").compareTo(cap.getValue().getAdjustedClose()));
    }

    @Test
    void provider_failure_does_not_throw_and_skips_writes() {
        when(catalog.isSupported("THYAO")).thenReturn(true);
        when(provider.fetchHistory(eq("THYAO"), any(), any()))
                .thenReturn(
                        BistProviderResult.failure(
                                new BistProviderError(
                                        BistProviderSource.IS_YATIRIM,
                                        "THYAO",
                                        "http fail",
                                        null,
                                        500,
                                        Instant.now())));
        BistEquityIngestSymbolResult r = service.ingestHistory("THYAO", LocalDate.now().minusDays(1), LocalDate.now());
        assertFalse(r.success());
        verify(repository, never()).save(any());
    }

    @Test
    void provider_exception_handled() {
        when(catalog.isSupported("THYAO")).thenReturn(true);
        when(provider.fetchHistory(eq("THYAO"), any(), any())).thenThrow(new RuntimeException("boom"));
        BistEquityIngestSymbolResult r = service.ingestHistory("THYAO", LocalDate.now().minusDays(1), LocalDate.now());
        assertFalse(r.success());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("provider_exception")));
    }

    @Test
    void partial_result_still_writes_and_merges_warnings() {
        when(catalog.isSupported("THYAO")).thenReturn(true);
        LocalDate d = LocalDate.of(2025, 4, 1);
        when(provider.fetchHistory(eq("THYAO"), any(), any()))
                .thenReturn(
                        BistProviderResult.partial(
                                List.of(row("THYAO", d, new BigDecimal("7"))), List.of("parse_warn")));
        when(repository.findBySymbolAndSourceAndTimestamp(eq("THYAO"), eq("IS_YATIRIM"), any()))
                .thenReturn(Optional.empty());
        BistEquityIngestSymbolResult r = service.ingestHistory("THYAO", d, d);
        assertTrue(r.success());
        assertTrue(r.warnings().contains("parse_warn"));
        verify(repository).save(any());
    }

    @Test
    void long_span_invokes_provider_in_chunks() {
        when(catalog.isSupported("THYAO")).thenReturn(true);
        when(bistProperties.getHistoryFetchChunkDays()).thenReturn(40);
        when(bistProperties.getDelayMsBetweenHistoryChunks()).thenReturn(0);
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 4, 1);
        when(provider.fetchHistory(eq("THYAO"), any(), any())).thenReturn(BistProviderResult.success(List.of()));

        service.ingestHistory("THYAO", from, to);
        verify(provider, times(3)).fetchHistory(eq("THYAO"), any(), any());
    }
}
