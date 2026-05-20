package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.DebtInstrumentResponse;
import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.api.dto.ViopHistoryResponse;
import com.nurseli.marketdata.api.dto.ViopMarketContractDto;
import com.nurseli.marketdata.api.dto.ViopMarketSnapshotDto;
import com.nurseli.marketdata.api.dto.ViopPricePoint;
import com.nurseli.marketdata.api.dto.ViopSnapshotResponse;
import com.nurseli.marketdata.application.DebtQueryService;
import com.nurseli.marketdata.application.ViopMarketDataService;
import com.nurseli.marketdata.application.ViopQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ViopMarketController.class, DebtMarketController.class})
@AutoConfigureMockMvc(addFilters = false)
class MarketDomainControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ViopQueryService viopQueryService;

    @MockitoBean
    private ViopMarketDataService viopMarketDataService;

    @MockitoBean
    private DebtQueryService debtQueryService;

    @Test
    void viopEndpointsShouldBeReachable() throws Exception {
        when(viopMarketDataService.listContracts(false))
                .thenReturn(List.of(new ViopMarketContractDto(
                        "XU0300626",
                        "XU030",
                        "XU030",
                        "XU0300626",
                        6,
                        2026,
                        "INDEX",
                        "INDEX_FUTURES",
                        "PRICE_SERIES",
                        true,
                        false,
                        "İş Yatırım",
                        15)));
        when(viopMarketDataService.getContract("F_XU0301226"))
                .thenReturn(new ViopMarketContractDto(
                        "F_XU0301226",
                        "XU030",
                        "BIST 30 Vadeli",
                        "XU030 Aralık 2026 Vadeli",
                        12,
                        2026,
                        "INDEX",
                        "INDEX_FUTURES",
                        "PRICE_SERIES",
                        true,
                        false,
                        "İş Yatırım",
                        15));
        when(viopQueryService.latest()).thenReturn(List.of(
                new ViopSnapshotResponse(
                        "XU0300626",
                        "2026-06-30",
                        "2026-06",
                        new BigDecimal("12400"),
                        new BigDecimal("12335"),
                        new BigDecimal("65"),
                        new BigDecimal("6.4"),
                        new BigDecimal("1488.00"),
                        "LONG",
                        145000L,
                        25000L,
                        "PRICE_UP_OI_UP",
                        "VIOP_MVP",
                        LocalDateTime.now(),
                        120,
                        "EXACT",
                        "VIOP_PROVIDER",
                        120L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));
        when(viopQueryService.history("XU0300426", 7)).thenReturn(List.of());
        when(viopQueryService.oiHistory("XU0300426", 7)).thenReturn(List.of());

        when(viopMarketDataService.getSnapshot("F_XU0301226"))
                .thenReturn(new ViopMarketSnapshotDto(
                        "F_XU0301226",
                        "XU030",
                        "BIST 30 Vadeli",
                        "XU030 Aralık 2026 Vadeli",
                        12,
                        2026,
                        OffsetDateTime.parse("2026-05-13T19:30:47+03:00"),
                        new BigDecimal("19966"),
                        new BigDecimal("19990"),
                        new BigDecimal("19900"),
                        new BigDecimal("20180"),
                        new BigDecimal("19989"),
                        new BigDecimal("20166"),
                        new BigDecimal("20172"),
                        new BigDecimal("-177"),
                        new BigDecimal("-0.877200"),
                        new BigDecimal("61"),
                        new BigDecimal("12190230"),
                        new BigDecimal("19951"),
                        new BigDecimal("20166"),
                        new BigDecimal("20549"),
                        new BigDecimal("19353"),
                        new BigDecimal("1"),
                        new BigDecimal("23800"),
                        new BigDecimal("19970"),
                        new BigDecimal("20671"),
                        null,
                        new BigDecimal("19440"),
                        new BigDecimal("20730"),
                        null,
                        null,
                        null,
                        "İş Yatırım",
                        15,
                        "OK"));
        when(viopMarketDataService.getHistory(eq("F_XU0301226"), any(), any(), eq(60)))
                .thenReturn(new ViopHistoryResponse(
                        "F_XU0301226",
                        "XU030",
                        "BIST 30 Vadeli",
                        "XU030 Aralık 2026 Vadeli",
                        12,
                        2026,
                        "INDEX",
                        "INDEX_FUTURES",
                        "İş Yatırım",
                        15,
                        60,
                        "PRICE_SERIES",
                        "OK",
                        OffsetDateTime.parse("2026-05-13T20:00:40+03:00"),
                        LocalDateTime.of(2026, 5, 6, 0, 0),
                        LocalDateTime.of(2026, 5, 13, 23, 59, 59),
                        List.of(new ViopPricePoint(
                                "F_XU0301226",
                                Instant.parse("2026-05-13T17:00:00Z"),
                                new BigDecimal("20120"),
                                "IS_YATIRIM",
                                60))));

        mockMvc.perform(get("/api/market/viop/contracts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].contractCode").value("XU0300626"));
        mockMvc.perform(get("/api/market/viop/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].source").value("VIOP_MVP"))
                .andExpect(jsonPath("$.data[0].asOf").exists());
        mockMvc.perform(get("/api/market/viop/history").param("contract", "XU0300426")).andExpect(status().isOk());
        mockMvc.perform(get("/api/market/viop/oi-history").param("contract", "XU0300426")).andExpect(status().isOk());
        mockMvc.perform(get("/api/market/viop/contracts/F_XU0301226"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contractCode").value("F_XU0301226"))
                .andExpect(jsonPath("$.data.displayName").value("BIST 30 Vadeli"))
                .andExpect(jsonPath("$.data.delayMinutes").value(15));
        mockMvc.perform(get("/api/market/viop/contracts/F_XU0301226/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceLabel").value("İş Yatırım"))
                .andExpect(jsonPath("$.data.delayMinutes").value(15));
        mockMvc.perform(get("/api/market/viop/contracts/F_XU0301226/history")
                        .param("from", "2026-05-06T00:00:00")
                        .param("to", "2026-05-13T23:59:59")
                        .param("period", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chartType").value("PRICE_SERIES"))
                .andExpect(jsonPath("$.data.dataQuality").value("OK"));
    }

    @Test
    void debtEndpointsShouldBeReachable() throws Exception {
        when(debtQueryService.catalog()).thenReturn(List.of(
                new DebtInstrumentResponse(
                        "TRT010531T16", "TR Hazine Bonosu", "Hazine", "2031-05-01", null, null, null)));
        when(debtQueryService.latest()).thenReturn(List.of(
                new DebtSnapshotResponse(
                        "TRT010531T16",
                        new BigDecimal("94.22"),
                        new BigDecimal("38.40"),
                        "2031-05-01",
                        1800L,
                        null,
                        "DEBT_MVP",
                        LocalDateTime.now(),
                        "FALLBACK",
                        true,
                        false,
                        "BOND_PRICE_PERFORMANCE",
                        "PRICE",
                        false,
                        null,
                        null,
                        null)));
        when(debtQueryService.history("TRT010531T16", 7)).thenReturn(List.of());

        mockMvc.perform(get("/api/market/debt/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].isin").value("TRT010531T16"));
        mockMvc.perform(get("/api/market/debt/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].source").value("DEBT_MVP"))
                .andExpect(jsonPath("$.data[0].asOf").exists());
        mockMvc.perform(get("/api/market/debt/history").param("isin", "TRT010531T16")).andExpect(status().isOk());
    }
}
