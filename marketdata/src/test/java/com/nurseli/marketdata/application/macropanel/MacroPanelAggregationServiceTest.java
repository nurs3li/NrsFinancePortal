package com.nurseli.marketdata.application.macropanel;

import com.nurseli.marketdata.api.dto.deposit.DepositRateHistoryRowDto;
import com.nurseli.marketdata.api.dto.loan.LoanRateHistoryPointDto;
import com.nurseli.marketdata.api.dto.loan.LoanRateHistorySeriesDto;
import com.nurseli.marketdata.api.dto.loan.LoanRatesHistoryResponse;
import com.nurseli.marketdata.api.dto.macropanel.InterestInflationMacroPanelResponse;
import com.nurseli.marketdata.api.dto.macropanel.NormalizedMacroSeriesDto;
import com.nurseli.marketdata.application.deposit.DepositRatesQueryService;
import com.nurseli.marketdata.application.inflation.InflationIndexQueryService;
import com.nurseli.marketdata.application.inflation.InflationMonthMetrics;
import com.nurseli.marketdata.application.loan.LoanRatesMacroService;
import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.InflationCpiProperties;
import com.nurseli.marketdata.config.InflationPpiProperties;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.domain.loan.LoanRateSubtype;
import com.nurseli.marketdata.application.DebtQueryService;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroPanelAggregationServiceTest {

    @Mock
    private EvdsProperties evdsProperties;
    @Mock
    private EvdsDebtClient evdsDebtClient;
    @Mock
    private InflationIndexQueryService inflationIndexQueryService;
    @Mock
    private DepositRatesProperties depositRatesProperties;
    @Mock
    private DepositRatesQueryService depositRatesQueryService;
    @Mock
    private LoanRatesMacroService loanRatesMacroService;
    @Mock
    private InflationCpiProperties inflationCpiProperties;
    @Mock
    private InflationPpiProperties inflationPpiProperties;
    @Mock
    private DebtQueryService debtQueryService;

    @InjectMocks
    private MacroPanelAggregationService service;

    @BeforeEach
    void defaults() {
        lenient().when(depositRatesProperties.isEnabled()).thenReturn(true);
        lenient().when(inflationCpiProperties.getBaseYear()).thenReturn(2003);
        lenient().when(inflationPpiProperties.getBaseYear()).thenReturn(2003);
        lenient().when(inflationIndexQueryService.indexPointsBetween(
                        any(InflationIndicatorType.class), any(), any()))
                .thenReturn(List.of());
        lenient().when(inflationIndexQueryService.latestMetrics(any(InflationIndicatorType.class)))
                .thenReturn(Optional.empty());
        lenient().when(loanRatesMacroService.historyFromDb(any(), any(), any()))
                .thenReturn(new LoanRatesHistoryResponse("EVDS", "WEEKLY", "PERCENT", List.of()));
    }

    @Test
    void build_whenEvdsDisabled_returnsEmptySeriesWithoutError() {
        when(evdsProperties.isEnabled()).thenReturn(false);
        when(debtQueryService.latest()).thenReturn(List.of());

        InterestInflationMacroPanelResponse r = service.build();

        assertNotNull(r.generatedAt());
        assertTrue(r.series().isEmpty());
        assertNotNull(r.derived());
        assertNotNull(r.governmentBonds());
    }

    @Test
    void build_whenDepositDbPopulated_skipsEvdsForThatSeries() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(debtQueryService.latest()).thenReturn(List.of());
        when(evdsProperties.getSeriesCode(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            if ("DEPOSIT_RATE_TRY_1M_WEEKLY".equals(k)) {
                return "TP_TRY_MT01";
            }
            if ("CPI_TR_INDEX".equals(k)) {
                return "TP_CPI";
            }
            if ("PPI_TR_INDEX".equals(k)) {
                return "TP_PPI";
            }
            if ("POLICY_RATE_TR".equals(k)) {
                return "TP_POL";
            }
            if ("TCMB_WEIGHTED_AVG_FUNDING_COST_TR".equals(k)) {
                return "TP_APIFON4";
            }
            if (k != null && k.startsWith("LOAN_RATE_")) {
                return "TP_LOAN";
            }
            if (k != null && k.startsWith("DEPOSIT_RATE_")) {
                return "TP_DEP_" + k;
            }
            return null;
        });

        LocalDate recent = LocalDate.now();
        when(depositRatesQueryService.history(eq("TRY"), eq("1M"), any(), any()))
                .thenReturn(List.of(new DepositRateHistoryRowDto(
                        "TP_TRY_MT01",
                        "TRY",
                        "1M",
                        recent,
                        new BigDecimal("45.5")
                )));

        EvdsSeriesPoint pt = new EvdsSeriesPoint(LocalDateTime.now().minusWeeks(1), BigDecimal.valueOf(2.5));
        when(evdsDebtClient.fetchSeriesAscending(anyString(), any(), any())).thenReturn(List.of(pt));

        InterestInflationMacroPanelResponse r = service.build();

        Optional<NormalizedMacroSeriesDto> try1m = r.series().stream()
                .filter(s -> "DEPOSIT_RATE_TRY_1M_WEEKLY".equals(s.logicalKey()))
                .findFirst();
        assertTrue(try1m.isPresent());
        assertFalse(try1m.get().observations().isEmpty());
        verify(evdsDebtClient, never()).fetchSeriesAscending(eq("TP_TRY_MT01"), any(), any());
    }

    @Test
    void build_whenEvdsEnabled_addsFxDepositSeriesWithMetadata() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(debtQueryService.latest()).thenReturn(List.of());

        when(evdsProperties.getSeriesCode(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            if ("CPI_TR_INDEX".equals(k)) {
                return "TP_CPI";
            }
            if ("PPI_TR_INDEX".equals(k)) {
                return "TP_PPI";
            }
            if ("POLICY_RATE_TR".equals(k)) {
                return "TP_POL";
            }
            if ("TCMB_WEIGHTED_AVG_FUNDING_COST_TR".equals(k)) {
                return "TP_APIFON4";
            }
            if (k != null && k.startsWith("LOAN_RATE_")) {
                return "TP_LOAN_" + k;
            }
            if (k != null && k.startsWith("DEPOSIT_RATE_TRY")) {
                return "TP_TRY_" + k;
            }
            if (k != null && k.startsWith("DEPOSIT_RATE_USD")) {
                return "TP_USD_" + k;
            }
            if (k != null && k.startsWith("DEPOSIT_RATE_EUR")) {
                return "TP_EUR_" + k;
            }
            return null;
        });

        EvdsSeriesPoint pt = new EvdsSeriesPoint(LocalDateTime.now().minusWeeks(1), BigDecimal.valueOf(2.5));
        when(evdsDebtClient.fetchSeriesAscending(anyString(), any(), any())).thenReturn(List.of(pt));
        LocalDate recent = LocalDate.now();
        lenient().when(depositRatesQueryService.history(anyString(), anyString(), any(), any()))
                .thenReturn(List.of());
        when(depositRatesQueryService.history(eq("USD"), eq("1M"), any(), any()))
                .thenReturn(List.of(new DepositRateHistoryRowDto(
                        "TP_USD_MT01",
                        "USD",
                        "1M",
                        recent,
                        new BigDecimal("3.25")
                )));

        InterestInflationMacroPanelResponse r = service.build();

        Optional<NormalizedMacroSeriesDto> usd1m = r.series().stream()
                .filter(s -> "DEPOSIT_RATE_USD_1M_WEEKLY".equals(s.logicalKey()))
                .findFirst();
        assertTrue(usd1m.isPresent());
        assertEquals("FX_DEPOSIT_RATE", usd1m.get().category());
        assertEquals("USD", usd1m.get().currency());
        assertEquals("1M", usd1m.get().tenor());
        assertEquals("FLOW", usd1m.get().dataType());
        assertFalse(usd1m.get().observations().isEmpty());
        verify(evdsDebtClient, never()).fetchSeriesAscending(eq("TP_USD_DEPOSIT_RATE_USD_1M_WEEKLY"), any(), any());
    }

    @Test
    void build_whenInflationDbPopulated_usesDbForDerived() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(debtQueryService.latest()).thenReturn(List.of());
        when(evdsProperties.getSeriesCode(anyString())).thenReturn("TP_X");
        lenient().when(inflationIndexQueryService.resolveSeriesCode(InflationIndicatorType.CPI)).thenReturn("TP_CPI");
        when(inflationIndexQueryService.latestMetrics(InflationIndicatorType.CPI))
                .thenReturn(Optional.of(new InflationMonthMetrics(
                        YearMonth.now(),
                        new BigDecimal("3200"),
                        new BigDecimal("2.1"),
                        new BigDecimal("55.5")
                )));

        EvdsSeriesPoint pt = new EvdsSeriesPoint(LocalDateTime.now().minusWeeks(1), BigDecimal.TEN);
        when(evdsDebtClient.fetchSeriesAscending(anyString(), any(), any())).thenReturn(List.of(pt));
        when(loanRatesMacroService.historyFromDb(any(Set.class), any(), any()))
                .thenReturn(new LoanRatesHistoryResponse("EVDS", "WEEKLY", "PERCENT", List.of()));

        InterestInflationMacroPanelResponse r = service.build();

        assertNotNull(r.derived());
        assertEquals(55.5, r.derived().cpiYoY());
    }
}
