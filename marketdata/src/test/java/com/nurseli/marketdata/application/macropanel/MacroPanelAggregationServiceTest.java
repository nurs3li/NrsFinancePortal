package com.nurseli.marketdata.application.macropanel;

import com.nurseli.marketdata.api.dto.macropanel.InterestInflationMacroPanelResponse;
import com.nurseli.marketdata.api.dto.macropanel.NormalizedMacroSeriesDto;
import com.nurseli.marketdata.application.DebtQueryService;
import com.nurseli.marketdata.application.EvdsMacroIndicatorService;
import com.nurseli.marketdata.application.inflation.InflationMacroService;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class MacroPanelAggregationServiceTest {

    @Mock
    private EvdsProperties evdsProperties;
    @Mock
    private EvdsDebtClient evdsDebtClient;
    @Mock
    private EvdsMacroIndicatorService evdsMacroIndicatorService;
    @Mock
    private InflationMacroService inflationMacroService;
    @Mock
    private DebtQueryService debtQueryService;

    @InjectMocks
    private MacroPanelAggregationService service;

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
    void build_whenEvdsEnabled_addsFxDepositSeriesWithMetadata() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(debtQueryService.latest()).thenReturn(List.of());
        when(inflationMacroService.latest()).thenReturn(null);
        when(evdsMacroIndicatorService.latestPolicyRateTr()).thenReturn(Optional.empty());

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

        InterestInflationMacroPanelResponse r = service.build();

        Optional<NormalizedMacroSeriesDto> usd1m = r.series().stream()
                .filter(s -> "DEPOSIT_RATE_USD_1M_WEEKLY".equals(s.logicalKey()))
                .findFirst();
        assertTrue(usd1m.isPresent());
        assertEquals("FX_DEPOSIT_RATE", usd1m.get().category());
        assertEquals("USD", usd1m.get().currency());
        assertEquals("1M", usd1m.get().tenor());
        assertEquals("FLOW", usd1m.get().dataType());
        assertEquals("WEEKLY", usd1m.get().frequency());
        assertEquals("PERCENT", usd1m.get().unit());
        assertEquals("EVDS", usd1m.get().source());
        assertFalse(usd1m.get().observations().isEmpty());
    }

    @Test
    void build_whenEvdsEnabled_addsWeightedFundingCostSeries() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(debtQueryService.latest()).thenReturn(List.of());
        when(inflationMacroService.latest()).thenReturn(null);
        when(evdsMacroIndicatorService.latestPolicyRateTr()).thenReturn(Optional.empty());

        when(evdsProperties.getSeriesCode(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            if ("TCMB_WEIGHTED_AVG_FUNDING_COST_TR".equals(k)) {
                return "TP_APIFON4";
            }
            return null;
        });

        EvdsSeriesPoint pt = new EvdsSeriesPoint(LocalDateTime.now().minusWeeks(1), BigDecimal.valueOf(42.5));
        when(evdsDebtClient.fetchSeriesAscending(anyString(), any(), any())).thenReturn(List.of(pt));

        InterestInflationMacroPanelResponse r = service.build();

        Optional<NormalizedMacroSeriesDto> funding = r.series().stream()
                .filter(s -> "TCMB_WEIGHTED_AVG_FUNDING_COST_TR".equals(s.logicalKey()))
                .findFirst();
        assertTrue(funding.isPresent());
        assertEquals("TP_APIFON4", funding.get().code());
        assertEquals("FUNDING_COST", funding.get().category());
        assertEquals("WEEKLY", funding.get().frequency());
        assertEquals("PERCENT", funding.get().unit());
        assertEquals("EVDS", funding.get().source());
        assertEquals("TCMB Ağırlıklı Ortalama Fonlama Maliyeti", funding.get().label());
        assertFalse(funding.get().observations().isEmpty());
    }
}
