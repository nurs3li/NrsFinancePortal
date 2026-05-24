package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.ApiEnvelopeAdvice;
import com.nurseli.marketdata.api.GlobalExceptionHandler;
import com.nurseli.marketdata.api.dto.BistBatchHistoryResponse;
import com.nurseli.marketdata.api.dto.BistEquityCandleResponse;
import com.nurseli.marketdata.api.dto.BistEquityHistoryResponse;
import com.nurseli.marketdata.api.dto.BistBackfillRequest;
import com.nurseli.marketdata.api.dto.BistBackfillResponse;
import com.nurseli.marketdata.api.dto.BistSymbolResponse;
import com.nurseli.marketdata.application.bist.BistEquityBackfillService;
import com.nurseli.marketdata.application.bist.BistEquityQueryService;
import com.nurseli.marketdata.application.bist.BistEquityUnsupportedSymbolException;
import com.nurseli.marketdata.config.BistProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BistEquityController.class, BistEquityAdminController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, ApiEnvelopeAdvice.class})
class BistEquityControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BistEquityQueryService bistEquityQueryService;

    @MockitoBean
    private BistProperties bistProperties;

    @MockitoBean
    private BistEquityBackfillService bistEquityBackfillService;

    @Test
    void symbols_returns_catalog_size() throws Exception {
        List<BistSymbolResponse> twenty =
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(
                                i ->
                                        new BistSymbolResponse(
                                                "S" + i,
                                                "N" + i,
                                                "sec",
                                                "BIST",
                                                "TRY",
                                                "S" + i + ".IS",
                                                "EQUITY",
                                                "TR"))
                        .toList();
        when(bistEquityQueryService.getSymbols()).thenReturn(twenty);

        mockMvc.perform(get("/api/market/equities/bist/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(20));
    }

    @Test
    void latest_all_empty_db_returns_200() throws Exception {
        when(bistEquityQueryService.getLatest()).thenReturn(List.of());
        mockMvc.perform(get("/api/market/equities/bist/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void latest_one_unsupported_returns_400() throws Exception {
        when(bistEquityQueryService.getLatest("BAD")).thenThrow(new BistEquityUnsupportedSymbolException("BAD"));
        mockMvc.perform(get("/api/market/equities/bist/BAD/latest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void latest_one_missing_returns_200_empty_body() throws Exception {
        when(bistEquityQueryService.getLatest("THYAO")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/market/equities/bist/THYAO/latest")).andExpect(status().isOk());
    }

    @Test
    void history_uses_default_range_when_params_omitted() throws Exception {
        when(bistProperties.getDefaultLookbackYears()).thenReturn(2);
        when(bistEquityQueryService.getHistory(eq("THYAO"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(
                        List.of(
                                new BistEquityHistoryResponse(
                                        "THYAO",
                                        LocalDate.of(2025, 1, 1),
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        BigDecimal.TEN,
                                        BigDecimal.TEN,
                                        BigDecimal.TEN,
                                        null,
                                        null,
                                        null,
                                        "IS_YATIRIM",
                                        "PARTIAL")));

        mockMvc.perform(get("/api/market/equities/bist/THYAO/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        ArgumentCaptor<LocalDate> fromCap = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(bistEquityQueryService).getHistory(eq("THYAO"), fromCap.capture(), toCap.capture());
        assertFalse(toCap.getValue().isBefore(fromCap.getValue()));
    }

    @Test
    void history_invalid_range_400() throws Exception {
        when(bistProperties.getDefaultLookbackYears()).thenReturn(2);
        mockMvc.perform(
                        get("/api/market/equities/bist/THYAO/history")
                                .param("from", "2026-06-10")
                                .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void candles_endpoint_ok() throws Exception {
        when(bistProperties.getDefaultLookbackYears()).thenReturn(1);
        when(bistEquityQueryService.getCandles(eq("ASELS"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(
                        List.of(
                                new BistEquityCandleResponse(
                                        "ASELS",
                                        LocalDate.of(2025, 2, 1),
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        BigDecimal.TEN,
                                        BigDecimal.TEN,
                                        BigDecimal.TEN,
                                        null,
                                        null,
                                        null,
                                        "IS_YATIRIM",
                                        "PARTIAL")));

        mockMvc.perform(get("/api/market/equities/bist/ASELS/candles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].open").exists())
                .andExpect(jsonPath("$.data[0].high").exists())
                .andExpect(jsonPath("$.data[0].low").exists())
                .andExpect(jsonPath("$.data[0].close").exists())
                .andExpect(jsonPath("$.data[0].volume").exists());
    }

    @Test
    void batch_history_requires_symbols_param() throws Exception {
        mockMvc.perform(get("/api/market/equities/bist/batch-history")).andExpect(status().isBadRequest());
    }

    @Test
    void batch_history_parses_symbols() throws Exception {
        when(bistProperties.getDefaultLookbackYears()).thenReturn(2);
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        when(bistEquityQueryService.getBatchHistory(
                        eq(List.of("THYAO", "ASELS")), eq(from), eq(to)))
                .thenReturn(new BistBatchHistoryResponse(Map.of("THYAO", List.of(), "ASELS", List.of())));

        mockMvc.perform(
                        get("/api/market/equities/bist/batch-history")
                                .param("symbols", " THYAO , ASELS ")
                                .param("from", "2025-01-01")
                                .param("to", "2025-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.historiesBySymbol.THYAO").isArray())
                .andExpect(jsonPath("$.data.historiesBySymbol.ASELS").isArray());
    }

    @Test
    void backfill_explicit_symbols() throws Exception {
        when(bistEquityBackfillService.runBackfill(any()))
                .thenReturn(
                        new BistBackfillResponse(
                                2,
                                2,
                                0,
                                10,
                                10,
                                0,
                                LocalDate.of(2024, 1, 1),
                                LocalDate.of(2024, 1, 31),
                                "IS_YATIRIM",
                                List.of()));

        mockMvc.perform(
                        post("/api/admin/market/equities/bist/backfill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"symbols":["THYAO","ASELS"],"from":"2024-01-01","to":"2024-01-31","provider":"IS_YATIRIM"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedSymbols").value(2));

        ArgumentCaptor<BistBackfillRequest> cap = ArgumentCaptor.forClass(BistBackfillRequest.class);
        verify(bistEquityBackfillService).runBackfill(cap.capture());
        assertEquals(List.of("THYAO", "ASELS"), cap.getValue().getSymbols());
    }

    @Test
    void backfill_null_body_delegates_to_service() throws Exception {
        when(bistEquityBackfillService.runBackfill(any()))
                .thenReturn(
                        new BistBackfillResponse(
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                LocalDate.now(),
                                LocalDate.now(),
                                "IS_YATIRIM",
                                List.of()));

        mockMvc.perform(
                        post("/api/admin/market/equities/bist/backfill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk());

        ArgumentCaptor<BistBackfillRequest> cap = ArgumentCaptor.forClass(BistBackfillRequest.class);
        verify(bistEquityBackfillService).runBackfill(cap.capture());
        assertNull(cap.getValue().getSymbols());
    }

    @Test
    void backfill_from_after_to_400() throws Exception {
        mockMvc.perform(
                        post("/api/admin/market/equities/bist/backfill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"symbols":["THYAO"],"from":"2026-06-10","to":"2026-01-01","provider":"IS_YATIRIM"}
                                        """))
                .andExpect(status().isBadRequest());
    }
}
