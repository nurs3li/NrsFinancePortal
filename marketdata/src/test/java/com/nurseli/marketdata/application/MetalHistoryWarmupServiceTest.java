package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.ingest.IsYatirimMetalUsdIngestService;
import com.nurseli.marketdata.application.ingest.MetalHistoryWarmupService;
import com.nurseli.marketdata.application.ingest.MetalPriceIngestService;
import com.nurseli.marketdata.config.MarketMetalsHistoryWarmupProperties;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetalHistoryWarmupServiceTest {

    @Mock
    private MarketPriceHistoryRepository repository;
    @Mock
    private MetalPriceIngestService metalPriceIngestService;
    @Mock
    private IsYatirimMetalUsdIngestService isYatirimMetalUsdIngestService;

    private MetalHistoryWarmupService service;

    @BeforeEach
    void setUp() {
        MarketMetalsHistoryWarmupProperties properties = new MarketMetalsHistoryWarmupProperties();
        properties.setEnabled(true);
        properties.setMaxRangeChunkDays(30);
        properties.setDelayBetweenChunksMs(0);
        service = new MetalHistoryWarmupService(
                properties,
                repository,
                metalPriceIngestService,
                isYatirimMetalUsdIngestService);
    }

    @Test
    void coverage_usesAnySourceRowsForGramGold() {
        MarketPriceHistory oldest = row(LocalDate.of(2026, 5, 1));
        MarketPriceHistory newest = row(LocalDate.of(2026, 5, 5));
        when(repository.findTopBySymbolOrderByTimestampAsc("XAU_TRY")).thenReturn(Optional.of(oldest));
        when(repository.findTopBySymbolOrderByTimestampDesc("XAU_TRY")).thenReturn(Optional.of(newest));
        when(repository.countDistinctTradeDaysBySymbolAndTimestampRange(eq("XAU_TRY"), any(), any())).thenReturn(5L);

        MetalHistoryWarmupService.MetalHistoryCoverage coverage =
                service.getCoverage("ALTIN_TRY", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

        assertThat(coverage.ready()).isTrue();
        assertThat(coverage.symbol()).isEqualTo("XAU_TRY");
        assertThat(coverage.availableDays()).isEqualTo(5);
    }

    @Test
    void warmupSync_gramGold_callsRangeBackfill() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 1);
        when(metalPriceIngestService.backfillRange(from, to, "test"))
                .thenReturn(new MetalPriceIngestService.IngestSummary(12, from, to));

        service.warmupSymbolSync("ALTIN_TRY", from, to, "test");

        verify(metalPriceIngestService).backfillRange(from, to, "test");
    }

    @Test
    void warmupSync_usdOunce_splitsLongRangeIntoChunks() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 15);
        when(isYatirimMetalUsdIngestService.ingestRange(any(), any(), any(), eq(false)))
                .thenReturn(new IsYatirimMetalUsdIngestService.IngestSummary(1, 0, 0, 1));

        service.warmupSymbolSync("XAU_USD_OZ", from, to, "test");

        verify(isYatirimMetalUsdIngestService).ingestRange(
                eq(PreciousMetalUsdCatalog.byCanonicalOrNull("XAU_USD_OZ")),
                eq(from),
                eq(LocalDate.of(2026, 1, 30)),
                eq(false));
        verify(isYatirimMetalUsdIngestService).ingestRange(
                eq(PreciousMetalUsdCatalog.byCanonicalOrNull("XAU_USD_OZ")),
                eq(LocalDate.of(2026, 1, 31)),
                eq(to),
                eq(false));
    }

    private static MarketPriceHistory row(LocalDate date) {
        MarketPriceHistory history = new MarketPriceHistory();
        history.setTimestamp(LocalDateTime.of(date, java.time.LocalTime.MIDNIGHT));
        return history;
    }
}
