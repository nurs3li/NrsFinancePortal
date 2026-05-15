package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.loan.LoanRateHistoryPointDto;
import com.nurseli.marketdata.api.dto.loan.LoanRateHistorySeriesDto;
import com.nurseli.marketdata.api.dto.loan.LoanRatesHistoryResponse;
import com.nurseli.marketdata.api.dto.loan.LoanRatesLatestResponse;
import com.nurseli.marketdata.application.loan.LoanRatesMacroService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoanRatesMarketController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoanRatesMarketControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanRatesMacroService loanRatesMacroService;

    @Test
    void latestReturnsEnvelopePayload() throws Exception {
        when(loanRatesMacroService.latest()).thenReturn(new LoanRatesLatestResponse(
                "EVDS",
                "WEEKLY",
                "PERCENT",
                LocalDate.of(2026, 5, 8),
                List.of()
        ));
        mockMvc.perform(get("/api/market/macro/loan-rates/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data.asOf").value("2026-05-08"));
    }

    @Test
    void historyReturnsSeries() throws Exception {
        when(loanRatesMacroService.history(any(), eq(LocalDate.of(2026, 5, 1)), eq(LocalDate.of(2026, 5, 15))))
                .thenReturn(new LoanRatesHistoryResponse(
                        "EVDS",
                        "WEEKLY",
                        "PERCENT",
                        List.of(new LoanRateHistorySeriesDto(
                                "CONSUMER_TRY",
                                "İhtiyaç",
                                "TP_KTF10",
                                List.of(new LoanRateHistoryPointDto("2026-05-08", new BigDecimal("61.55")))
                        ))
                ));
        mockMvc.perform(get("/api/market/macro/loan-rates/history")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.series[0].type").value("CONSUMER_TRY"))
                .andExpect(jsonPath("$.data.series[0].points[0].value").value(61.55));
    }
}
