package com.nurseli.marketdata.application.bist;

import com.nurseli.marketdata.api.dto.BistBatchHistoryResponse;
import com.nurseli.marketdata.api.dto.BistEquityHistoryResponse;
import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.bist.BistDataQuality;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BistEquityQueryServiceTest {

    @Mock private MarketPriceHistoryRepository repository;
    @Mock private BistEquityIngestService ingestService;
    private final BistSymbolCatalog catalog = new BistSymbolCatalog();
    private final BistProperties bistProperties = new BistProperties();

    private BistEquityQueryService service;

    @BeforeEach
    void setUp() {
        bistProperties.setEnabled(false);
        service = new BistEquityQueryService(repository, catalog, bistProperties, ingestService);
    }

    @Test
    void unsupported_symbol_throws() {
        assertThrows(BistEquityUnsupportedSymbolException.class, () -> service.getHistory("NOTHERE", LocalDate.now(), LocalDate.now()));
    }

    @Test
    void history_sorted_ascending_by_date() {
        LocalDate d1 = LocalDate.of(2025, 1, 1);
        LocalDate d2 = LocalDate.of(2025, 1, 2);
        MarketPriceHistory a = mph("THYAO", d2, new BigDecimal("102"));
        MarketPriceHistory b = mph("THYAO", d1, new BigDecimal("100"));
        LocalDate from = d1;
        LocalDate to = d2;
        LocalDateTime start = from.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        LocalDateTime endEx = to.plusDays(1).atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        when(repository.findBySymbolAndSourceAndTimestampRange(eq("THYAO"), eq("IS_YATIRIM"), eq(start), eq(endEx)))
                .thenReturn(List.of(a, b));

        List<BistEquityHistoryResponse> h = service.getHistory("THYAO", from, to);
        assertEquals(2, h.size());
        assertEquals(d1, h.get(0).date());
        assertEquals(d2, h.get(1).date());
    }

    @Test
    void candle_open_uses_previous_adjusted_close() {
        LocalDate d1 = LocalDate.of(2025, 2, 1);
        LocalDate d2 = LocalDate.of(2025, 2, 2);
        MarketPriceHistory first = mph("THYAO", d1, new BigDecimal("10"));
        first.setAdjustedAverage(new BigDecimal("10.5"));
        first.setAdjustedHigh(new BigDecimal("11"));
        first.setAdjustedLow(new BigDecimal("9"));
        first.setAdjustedVolume(new BigDecimal("1000"));
        first.setRawClose(new BigDecimal("9.9"));
        first.setDataQuality(BistDataQuality.HISTORICAL.name());
        MarketPriceHistory second = mph("THYAO", d2, new BigDecimal("12"));
        second.setAdjustedHigh(new BigDecimal("13"));
        second.setAdjustedLow(new BigDecimal("11"));
        second.setAdjustedVolume(new BigDecimal("2000"));
        second.setRawClose(new BigDecimal("11.5"));
        second.setDataQuality(BistDataQuality.HISTORICAL.name());

        List<BistEquityHistoryResponse> bars =
                BistEquityQueryService.toHistoryBars("THYAO", List.of(first, second));
        assertEquals(0, new BigDecimal("10.5").compareTo(bars.get(0).open()));
        assertEquals(BistDataQuality.PARTIAL.name(), bars.get(0).dataQuality());
        assertEquals(0, new BigDecimal("10").compareTo(bars.get(1).open()));
        assertEquals(BistDataQuality.PARTIAL.name(), bars.get(1).dataQuality());
    }

    @Test
    void first_candle_open_falls_back_to_adjusted_close_when_no_average() {
        LocalDate d1 = LocalDate.of(2025, 2, 10);
        MarketPriceHistory first = mph("THYAO", d1, new BigDecimal("22"));
        first.setAdjustedHigh(new BigDecimal("23"));
        first.setAdjustedLow(new BigDecimal("21"));
        first.setAdjustedVolume(BigDecimal.TEN);
        first.setDataQuality(BistDataQuality.PARTIAL.name());
        List<BistEquityHistoryResponse> bars = BistEquityQueryService.toHistoryBars("THYAO", List.of(first));
        assertEquals(0, new BigDecimal("22").compareTo(bars.get(0).open()));
    }

    @Test
    void latest_returns_max_date() {
        LocalDate older = LocalDate.of(2025, 3, 1);
        LocalDate newer = LocalDate.of(2025, 3, 10);
        MarketPriceHistory latest = mph("THYAO", newer, new BigDecimal("55"));
        when(repository.findTopBySymbolAndSourceOrderByTimestampDesc("THYAO", "IS_YATIRIM"))
                .thenReturn(Optional.of(latest));
        when(repository.findTop2BySymbolAndSourceOrderByTimestampDesc("THYAO", "IS_YATIRIM"))
                .thenReturn(List.of(latest, mph("THYAO", older, new BigDecimal("50"))));

        assertTrue(service.getLatest("THYAO").isPresent());
        assertEquals(0, new BigDecimal("55").compareTo(service.getLatest("THYAO").get().adjustedClose()));
    }

    @Test
    void db_empty_and_enabled_triggers_ingest() {
        bistProperties.setEnabled(true);
        service = new BistEquityQueryService(repository, catalog, bistProperties, ingestService);
        LocalDate from = LocalDate.of(2025, 5, 1);
        LocalDate to = LocalDate.of(2025, 5, 2);
        LocalDateTime start = from.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        LocalDateTime endEx = to.plusDays(1).atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        when(repository.findBySymbolAndSourceAndTimestampRange(eq("THYAO"), eq("IS_YATIRIM"), eq(start), eq(endEx)))
                .thenReturn(List.of())
                .thenReturn(List.of(mph("THYAO", from, new BigDecimal("1"))));

        List<BistEquityHistoryResponse> h = service.getHistory("THYAO", from, to);
        assertEquals(1, h.size());
        verify(ingestService).ingestHistory(eq("THYAO"), eq(from), eq(to));
    }

    @Test
    void db_empty_and_disabled_does_not_call_ingest() {
        bistProperties.setEnabled(false);
        service = new BistEquityQueryService(repository, catalog, bistProperties, ingestService);
        LocalDate from = LocalDate.of(2025, 5, 1);
        LocalDate to = LocalDate.of(2025, 5, 2);
        LocalDateTime start = from.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        LocalDateTime endEx = to.plusDays(1).atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        when(repository.findBySymbolAndSourceAndTimestampRange(eq("THYAO"), eq("IS_YATIRIM"), eq(start), eq(endEx)))
                .thenReturn(List.of());

        assertTrue(service.getHistory("THYAO", from, to).isEmpty());
        verify(ingestService, never()).ingestHistory(any(), any(), any());
    }

    @Test
    void batch_history_groups_by_symbol() {
        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 6, 2);
        LocalDateTime start = from.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        LocalDateTime endEx = to.plusDays(1).atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        when(repository.findBySymbolsAndSourceAndTimestampRange(
                        eq(List.of("THYAO", "ASELS")), eq("IS_YATIRIM"), eq(start), eq(endEx)))
                .thenReturn(
                        List.of(
                                mph("THYAO", from, new BigDecimal("1")),
                                mph("ASELS", from, new BigDecimal("2"))));

        BistBatchHistoryResponse batch = service.getBatchHistory(List.of("THYAO", "ASELS"), from, to);
        assertEquals(1, batch.historiesBySymbol().get("THYAO").size());
        assertEquals(1, batch.historiesBySymbol().get("ASELS").size());
    }

    @Test
    void batch_history_unsupported_symbol_returns_empty_series() {
        LocalDate from = LocalDate.of(2025, 7, 1);
        LocalDate to = LocalDate.of(2025, 7, 5);
        LocalDateTime start = from.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        LocalDateTime endEx = to.plusDays(1).atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime();
        when(repository.findBySymbolsAndSourceAndTimestampRange(
                        eq(List.of("THYAO")), eq("IS_YATIRIM"), eq(start), eq(endEx)))
                .thenReturn(List.of());

        BistBatchHistoryResponse batch = service.getBatchHistory(List.of("THYAO", "NOTREAL"), from, to);
        assertTrue(batch.historiesBySymbol().containsKey("NOTREAL"));
        assertTrue(batch.historiesBySymbol().get("NOTREAL").isEmpty());
    }

    private static MarketPriceHistory mph(String sym, LocalDate d, BigDecimal adjClose) {
        MarketPriceHistory m = new MarketPriceHistory();
        m.setSymbol(sym);
        m.setSource("IS_YATIRIM");
        m.setTimestamp(d.atStartOfDay(BistEquityDailyConstants.IST).toLocalDateTime());
        m.setAdjustedClose(adjClose);
        m.setBuyPrice(adjClose);
        m.setSellPrice(adjClose);
        m.setCurrency("TRY");
        return m;
    }
}
