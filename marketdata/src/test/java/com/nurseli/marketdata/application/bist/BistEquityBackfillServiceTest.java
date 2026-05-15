package com.nurseli.marketdata.application.bist;

import com.nurseli.marketdata.api.dto.BistBackfillRequest;
import com.nurseli.marketdata.api.dto.BistBackfillStatus;
import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BistEquityBackfillServiceTest {

    @Mock private BistEquityIngestService ingestService;
    @Mock private BistSymbolCatalog catalog;
    private BistProperties props;

    private BistEquityBackfillService service;

    @BeforeEach
    void setUp() {
        props = new BistProperties();
        service = new BistEquityBackfillService(ingestService, catalog, props);
    }

    @Test
    void disabled_returns_disabled_items() {
        props.setEnabled(false);
        BistBackfillRequest req = new BistBackfillRequest();
        req.setSymbols(List.of("THYAO"));
        req.setFrom(LocalDate.of(2025, 1, 1));
        req.setTo(LocalDate.of(2025, 1, 31));
        req.setProvider("IS_YATIRIM");

        var r = service.runBackfill(req);
        assertEquals(1, r.requestedSymbols());
        assertEquals(0, r.successfulSymbols());
        assertEquals(0, r.failedSymbols());
        assertEquals(BistBackfillStatus.DISABLED, r.items().getFirst().status());
    }

    @Test
    void empty_request_symbols_uses_catalog() {
        props.setEnabled(false);
        when(catalog.getAll())
                .thenReturn(
                        List.of(
                                new BistSymbolMetadata("A", "A", "A.IS", "d", "s", "BIST", "TRY", "EQUITY", "TR"),
                                new BistSymbolMetadata("B", "B", "B.IS", "d", "s", "BIST", "TRY", "EQUITY", "TR")));

        var r = service.runBackfill(new BistBackfillRequest());
        assertEquals(2, r.requestedSymbols());
    }

    @Test
    void from_after_to_invalid() {
        props.setEnabled(true);
        BistBackfillRequest req = new BistBackfillRequest();
        req.setSymbols(List.of("THYAO"));
        req.setFrom(LocalDate.of(2026, 6, 10));
        req.setTo(LocalDate.of(2026, 1, 1));
        req.setProvider("IS_YATIRIM");
        assertThrows(InvalidRequestException.class, () -> service.runBackfill(req));
    }

    @Test
    void unsupported_symbol_skipped_not_aborting() {
        props.setEnabled(true);
        BistBackfillRequest req = new BistBackfillRequest();
        req.setSymbols(List.of("THYAO", "NOTREAL"));
        req.setFrom(LocalDate.of(2025, 1, 1));
        req.setTo(LocalDate.of(2025, 1, 10));
        req.setProvider("IS_YATIRIM");
        when(catalog.isSupported("THYAO")).thenReturn(true);
        when(catalog.isSupported("NOTREAL")).thenReturn(false);
        when(ingestService.ingestHistory(eq("THYAO"), any(), any()))
                .thenReturn(BistEquityIngestSymbolResult.of("THYAO", 1, 1, 0, true, List.of()));

        var r = service.runBackfill(req);
        assertEquals(2, r.requestedSymbols());
        assertEquals(1, r.successfulSymbols());
        assertEquals(0, r.failedSymbols());
        assertEquals(BistBackfillStatus.SKIPPED, r.items().stream().filter(i -> "NOTREAL".equals(i.symbol())).findFirst().orElseThrow().status());
    }

    @Test
    void one_symbol_failure_continues_others() {
        props.setEnabled(true);
        BistBackfillRequest req = new BistBackfillRequest();
        req.setSymbols(List.of("THYAO", "ASELS"));
        req.setFrom(LocalDate.of(2025, 1, 1));
        req.setTo(LocalDate.of(2025, 1, 10));
        req.setProvider("IS_YATIRIM");
        when(catalog.isSupported("THYAO")).thenReturn(true);
        when(catalog.isSupported("ASELS")).thenReturn(true);
        when(ingestService.ingestHistory(eq("THYAO"), any(), any()))
                .thenReturn(BistEquityIngestSymbolResult.of("THYAO", 0, 0, 0, false, List.of("err")));
        when(ingestService.ingestHistory(eq("ASELS"), any(), any()))
                .thenReturn(BistEquityIngestSymbolResult.of("ASELS", 2, 2, 0, true, List.of()));

        var r = service.runBackfill(req);
        assertEquals(2, r.requestedSymbols());
        assertEquals(1, r.successfulSymbols());
        assertEquals(1, r.failedSymbols());
    }

    @Test
    void invalid_provider() {
        props.setEnabled(true);
        BistBackfillRequest req = new BistBackfillRequest();
        req.setSymbols(List.of("THYAO"));
        req.setFrom(LocalDate.of(2025, 1, 1));
        req.setTo(LocalDate.of(2025, 1, 10));
        req.setProvider("YAHOO");
        assertThrows(InvalidRequestException.class, () -> service.runBackfill(req));
    }
}
