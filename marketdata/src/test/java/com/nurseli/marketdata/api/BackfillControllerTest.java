package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.DebtHistoryWarmupResponse;
import com.nurseli.marketdata.application.ingest.CryptoHistoryWarmupService;
import com.nurseli.marketdata.application.ingest.DebtHistoryWarmupService;
import com.nurseli.marketdata.application.ingest.FundPriceIngestService;
import com.nurseli.marketdata.application.ingest.IsyatirimMetalUsdBackfillService;
import com.nurseli.marketdata.application.ingest.MarketPriceBackfillService;
import com.nurseli.marketdata.application.ingest.ViopBackfillRunner;
import com.nurseli.marketdata.application.bist.BistEquityBackfillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BackfillController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("ops")
@TestPropertySource(properties = "app.internal.backfill-token=test-token")
class BackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketPriceBackfillService marketPriceBackfillService;
    @MockitoBean
    private ViopBackfillRunner viopBackfillRunner;
    @MockitoBean
    private BistEquityBackfillService bistEquityBackfillService;
    @MockitoBean
    private IsyatirimMetalUsdBackfillService isyatirimMetalUsdBackfillService;
    @MockitoBean
    private CryptoHistoryWarmupService cryptoHistoryWarmupService;
    @MockitoBean
    private DebtHistoryWarmupService debtHistoryWarmupService;
    @MockitoBean
    private FundPriceIngestService fundPriceIngestService;

    @Test
    void debtHistoryEndpoint_acceptsTokenAndReturnsWarmupStatus() throws Exception {
        when(debtHistoryWarmupService.requestWarmup(
                eq(List.of("TRT170227K64")),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalDate.of(2026, 5, 7)),
                eq("manual")))
                .thenReturn(new DebtHistoryWarmupResponse(
                        "QUEUED",
                        1,
                        1,
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 7),
                        "queued"));

        mockMvc.perform(post("/internal/market/backfill/debt-history")
                        .header("X-Nrs-Internal-Token", "test-token")
                        .param("isins", "TRT170227K64")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.requestedIsins").value(1));
    }
}
