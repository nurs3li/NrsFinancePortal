package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.MarketType;
import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import com.nurseli.marketdata.config.MarketStaleTailProperties;
import com.nurseli.marketdata.domain.price.EquityDailyCandle;
import com.nurseli.marketdata.infrastructure.persistence.CryptoDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.DebtSnapshotRepository;
import com.nurseli.marketdata.infrastructure.persistence.EquityDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.FxDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketStaleTailRepairServiceTest {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    @Mock
    private EquityDailyCandleRepository equityDailyCandleRepository;
    @Mock
    private CryptoDailyCandleRepository cryptoDailyCandleRepository;
    @Mock
    private FxDailyCandleRepository fxDailyCandleRepository;
    @Mock
    private MarketPriceHistoryRepository marketPriceHistoryRepository;
    @Mock
    private DebtSnapshotRepository debtSnapshotRepository;
    @Mock
    private EquityPriceIngestService equityPriceIngestService;
    @Mock
    private CryptoPriceIngestService cryptoPriceIngestService;
    @Mock
    private MarketPriceIngestService marketPriceIngestService;
    @Mock
    private MetalPriceIngestService metalPriceIngestService;
    @Mock
    private IsYatirimMetalUsdIngestService isYatirimMetalUsdIngestService;
    @Mock
    private MetalHistoryWarmupService metalHistoryWarmupService;
    @Mock
    private MarketMetalsIsyatirimProperties marketMetalsIsyatirimProperties;
    @Mock
    private DebtIngestService debtIngestService;

    private MarketStaleTailProperties staleTailProperties;
    private MarketStaleTailRepairService service;

    @BeforeEach
    void setUp() {
        staleTailProperties = new MarketStaleTailProperties();
        staleTailProperties.setEnabled(true);
        staleTailProperties.setDays(3);
        service =
                new MarketStaleTailRepairService(
                        staleTailProperties,
                        equityDailyCandleRepository,
                        cryptoDailyCandleRepository,
                        fxDailyCandleRepository,
                        marketPriceHistoryRepository,
                        debtSnapshotRepository,
                        equityPriceIngestService,
                        cryptoPriceIngestService,
                        marketPriceIngestService,
                        metalPriceIngestService,
                        isYatirimMetalUsdIngestService,
                        metalHistoryWarmupService,
                        marketMetalsIsyatirimProperties,
                        debtIngestService);
    }

    @Test
    void repair_equity_triggers_incremental_when_tail_stale() {
        LocalDate today = LocalDate.now(IST);
        LocalDate lastDay = today.minusDays(10);
        EquityDailyCandle candle = new EquityDailyCandle();
        candle.setSymbol("AAPL");
        candle.setAsOf(lastDay);
        when(equityDailyCandleRepository.findTopBySymbolOrderByAsOfDesc("AAPL"))
                .thenReturn(Optional.of(candle));

        service.repairBeforeRead(MarketType.EQUITY, "AAPL", today);

        verify(equityPriceIngestService, timeout(1000)).ingestIncrementalForSymbol("AAPL");
    }

    @Test
    void repair_equity_triggers_when_last_day_equals_cutoff() {
        LocalDate today = LocalDate.now(IST);
        LocalDate lastDay = today.minusDays(3);
        EquityDailyCandle candle = new EquityDailyCandle();
        candle.setSymbol("AAPL");
        candle.setAsOf(lastDay);
        when(equityDailyCandleRepository.findTopBySymbolOrderByAsOfDesc("AAPL"))
                .thenReturn(Optional.of(candle));

        service.repairBeforeRead(MarketType.EQUITY, "AAPL", today);

        verify(equityPriceIngestService, timeout(1000)).ingestIncrementalForSymbol("AAPL");
    }

    @Test
    void repair_equity_skips_when_fresh() {
        LocalDate today = LocalDate.now(IST);
        EquityDailyCandle candle = new EquityDailyCandle();
        candle.setSymbol("AAPL");
        candle.setAsOf(today.minusDays(1));
        when(equityDailyCandleRepository.findTopBySymbolOrderByAsOfDesc("AAPL"))
                .thenReturn(Optional.of(candle));

        service.repairBeforeRead(MarketType.EQUITY, "AAPL", today);

        sleepQuietly();
        verify(equityPriceIngestService, never()).ingestIncrementalForSymbol(eq("AAPL"));
    }

    @Test
    void repair_fx_triggers_backfill_when_tail_stale() {
        LocalDate today = LocalDate.now(IST);
        var candle = new com.nurseli.marketdata.domain.price.FxDailyCandle();
        candle.setSymbol("USDTRY");
        candle.setAsOf(today.minusDays(8));
        when(fxDailyCandleRepository.findTopBySymbolOrderByAsOfDesc("USDTRY"))
                .thenReturn(Optional.of(candle));

        service.repairBeforeRead(MarketType.FX, "USDTRY", today);

        verify(marketPriceIngestService, timeout(1000)).fetchAndSaveFxHistoryBackfill(anyInt());
    }

    @Test
    void repair_disabled_does_nothing() {
        staleTailProperties.setEnabled(false);
        service.repairBeforeRead(MarketType.EQUITY, "AAPL", LocalDate.now(IST));
        verify(equityPriceIngestService, never()).ingestIncrementalForSymbol(eq("AAPL"));
    }

    @Test
    void repair_metal_xauTry_emptyDb_triggersWarmup() {
        LocalDate today = LocalDate.now(IST);
        when(marketMetalsIsyatirimProperties.getDefaultLookbackYears()).thenReturn(2);
        when(marketPriceHistoryRepository.findTopBySymbolOrderByTimestampDesc("XAU_TRY"))
                .thenReturn(Optional.empty());

        service.repairBeforeRead(MarketType.METALS, "XAU_TRY", today);

        verify(metalHistoryWarmupService, timeout(1000))
                .warmupSymbolSync(eq("XAU_TRY"), any(LocalDate.class), eq(today), eq("stale-tail-empty-db"));
    }

    @Test
    void repair_metal_usd_emptyDb_triggersWarmup() {
        LocalDate today = LocalDate.now(IST);
        when(marketMetalsIsyatirimProperties.isEnabled()).thenReturn(true);
        when(marketMetalsIsyatirimProperties.getDefaultLookbackYears()).thenReturn(2);
        when(marketPriceHistoryRepository.findTopBySymbolAndSourceOrderByTimestampDesc("XAU_USD_OZ", "IS_YATIRIM"))
                .thenReturn(Optional.empty());

        service.repairBeforeRead(MarketType.METALS, "XAU_USD_OZ", today);

        verify(metalHistoryWarmupService, timeout(1000))
                .warmupSymbolSync(eq("XAU_USD_OZ"), any(LocalDate.class), eq(today), eq("stale-tail-empty-db"));
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
