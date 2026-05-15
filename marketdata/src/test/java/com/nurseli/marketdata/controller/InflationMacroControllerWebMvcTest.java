package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.inflation.InflationCompareResponse;
import com.nurseli.marketdata.api.dto.inflation.InflationCompareRowDto;
import com.nurseli.marketdata.application.inflation.InflationMacroService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InflationMacroController.class)
@AutoConfigureMockMvc(addFilters = false)
class InflationMacroControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InflationMacroService inflationMacroService;

    @Test
    void compare_returnsMergedRows() throws Exception {
        when(inflationMacroService.compare(eq(YearMonth.of(2025, 1)), eq(YearMonth.of(2025, 1)))).thenReturn(new InflationCompareResponse(List.of(
                new InflationCompareRowDto(
                        LocalDate.of(2025, 1, 1),
                        new BigDecimal("2.5"),
                        new BigDecimal("30"),
                        new BigDecimal("1.1"),
                        new BigDecimal("12")
                )
        )));
        mockMvc.perform(get("/api/market/macro/inflation/compare")
                        .param("from", "2025-01")
                        .param("to", "2025-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rows[0].month").value("2025-01-01"))
                .andExpect(jsonPath("$.data.rows[0].cpiAnnualChangePercent").value(30))
                .andExpect(jsonPath("$.data.rows[0].ppiAnnualChangePercent").value(12));
    }

    @Test
    void compare_badRequestWhenInvertedRange() throws Exception {
        mockMvc.perform(get("/api/market/macro/inflation/compare")
                        .param("from", "2025-02")
                        .param("to", "2025-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void history_badRequestForInvalidType() throws Exception {
        mockMvc.perform(get("/api/market/macro/inflation/history")
                        .param("type", "XYZ")
                        .param("from", "2024-01")
                        .param("to", "2024-03"))
                .andExpect(status().isBadRequest());
    }
}
