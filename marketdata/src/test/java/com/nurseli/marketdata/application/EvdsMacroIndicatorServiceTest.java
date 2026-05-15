package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.CpiTrMacroResponse;
import com.nurseli.marketdata.api.dto.TurkeyApproxRealRateResponse;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvdsMacroIndicatorServiceTest {

    @Mock
    private EvdsProperties evdsProperties;
    @Mock
    private EvdsDebtClient evdsDebtClient;
    @Mock
    private EvdsCpiTrService evdsCpiTrService;

    @InjectMocks
    private EvdsMacroIndicatorService service;

    @Test
    void approxTurkeyRealRate_prefersPolicyOverFunding() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(evdsCpiTrService.latestCpiTr()).thenReturn(Optional.of(new CpiTrMacroResponse(
                "TP_X",
                LocalDate.of(2026, 4, 1),
                new BigDecimal("4000"),
                new BigDecimal("1"),
                new BigDecimal("50"),
                null
        )));
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.POLICY_RATE_TR)).thenReturn("TP_POLICY");
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_POLICY"), any(), any())).thenReturn(List.of(
                new EvdsSeriesPoint(LocalDateTime.of(2026, 5, 1, 0, 0), new BigDecimal("45.5"))
        ));
        Optional<TurkeyApproxRealRateResponse> out = service.approxTurkeyRealRate();
        assertTrue(out.isPresent());
        TurkeyApproxRealRateResponse r = out.get();
        assertEquals(EvdsSeriesLogicalNames.POLICY_RATE_TR, r.sourceIndicatorCode());
        assertEquals("TP_POLICY", r.sourceEvdsSeriesCode());
        assertEquals(0, new BigDecimal("-4.5000").compareTo(r.approximateRealRatePercent()));
    }

    @Test
    void approxTurkeyRealRate_fallsBackToFundingWhenPolicyMissing() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(evdsCpiTrService.latestCpiTr()).thenReturn(Optional.of(new CpiTrMacroResponse(
                "TP_X",
                LocalDate.of(2026, 4, 1),
                new BigDecimal("4000"),
                new BigDecimal("1"),
                new BigDecimal("40"),
                null
        )));
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.POLICY_RATE_TR)).thenReturn(null);
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.TCMB_WEIGHTED_AVG_FUNDING_COST_TR)).thenReturn("TP_APIFON4");
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_APIFON4"), any(), any())).thenReturn(List.of(
                new EvdsSeriesPoint(LocalDateTime.of(2026, 5, 7, 0, 0), new BigDecimal("48"))
        ));
        Optional<TurkeyApproxRealRateResponse> out = service.approxTurkeyRealRate();
        assertTrue(out.isPresent());
        assertEquals(EvdsSeriesLogicalNames.TCMB_WEIGHTED_AVG_FUNDING_COST_TR, out.get().sourceIndicatorCode());
        assertEquals(0, new BigDecimal("8.0000").compareTo(out.get().approximateRealRatePercent()));
    }
}
