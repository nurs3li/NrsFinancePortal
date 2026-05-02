package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.DebtInstrumentResponse;
import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.api.dto.ViopContractResponse;
import com.nurseli.marketdata.api.dto.ViopSnapshotResponse;
import com.nurseli.marketdata.application.DebtQueryService;
import com.nurseli.marketdata.application.ViopQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ViopMarketController.class, DebtMarketController.class})
@AutoConfigureMockMvc(addFilters = false)
class MarketDomainControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ViopQueryService viopQueryService;

    @MockBean
    private DebtQueryService debtQueryService;

    @Test
    void viopEndpointsShouldBeReachable() throws Exception {
        when(viopQueryService.contracts()).thenReturn(List.of(
                new ViopContractResponse("XU0300626", "XU030", "2026-06-30", "FUTURES")
        ));
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
                        120L
                )
        ));
        when(viopQueryService.history("XU0300426", 7)).thenReturn(List.of());
        when(viopQueryService.oiHistory("XU0300426", 7)).thenReturn(List.of());

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
    }

    @Test
    void debtEndpointsShouldBeReachable() throws Exception {
        when(debtQueryService.catalog()).thenReturn(List.of(
                new DebtInstrumentResponse("TRT010531T16", "TR Hazine Bonosu", "Hazine", "2031-05-01")
        ));
        when(debtQueryService.latest()).thenReturn(List.of(
                new DebtSnapshotResponse(
                        "TRT010531T16",
                        new BigDecimal("94.22"),
                        new BigDecimal("38.40"),
                        "2031-05-01",
                        1800L,
                        new BigDecimal("15.50"),
                        "DEBT_MVP",
                        LocalDateTime.now(),
                        "FALLBACK",
                        true
                )
        ));
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
