package com.nurseli.marketdata.application.loan;

import com.nurseli.marketdata.api.dto.loan.LoanRatesHistoryResponse;
import com.nurseli.marketdata.api.dto.loan.LoanRatesLatestResponse;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.domain.loan.LoanRateSubtype;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanRatesMacroServiceTest {

    @Mock
    private EvdsProperties evdsProperties;
    @Mock
    private EvdsDebtClient evdsDebtClient;
    @Mock
    private LoanRatesPersistenceService loanRatesPersistenceService;
    @Mock
    private com.nurseli.marketdata.repository.LoanRateWeeklyObservationRepository loanRateWeeklyObservationRepository;

    @InjectMocks
    private LoanRatesMacroService service;

    @Test
    void latest_selectsLastPositiveObservationPerSeries() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_CONSUMER_TRY_WEEKLY)).thenReturn("TP_KTF10");
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_VEHICLE_TRY_WEEKLY)).thenReturn("TP_KTF11");
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_HOUSING_TRY_WEEKLY)).thenReturn("TP_KTF12");
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_COMMERCIAL_TRY_WEEKLY)).thenReturn("TP_KTF17");
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_KTF10"), any(), any())).thenReturn(List.of(
                new EvdsSeriesPoint(LocalDate.of(2026, 4, 24).atStartOfDay(), new BigDecimal("60")),
                new EvdsSeriesPoint(LocalDate.of(2026, 5, 8).atStartOfDay(), new BigDecimal("61.55"))
        ));
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_KTF11"), any(), any())).thenReturn(List.of(
                new EvdsSeriesPoint(LocalDate.of(2026, 5, 8).atStartOfDay(), new BigDecimal("39.68"))
        ));
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_KTF12"), any(), any())).thenReturn(List.of(
                new EvdsSeriesPoint(LocalDate.of(2026, 5, 8).atStartOfDay(), new BigDecimal("38.06"))
        ));
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_KTF17"), any(), any())).thenReturn(List.of(
                new EvdsSeriesPoint(LocalDate.of(2026, 5, 8).atStartOfDay(), new BigDecimal("55.39"))
        ));

        LoanRatesLatestResponse r = service.latest();
        assertEquals(4, r.items().size());
        assertEquals(LocalDate.of(2026, 5, 8), r.asOf());
        assertEquals(0, new BigDecimal("61.55").compareTo(
                r.items().stream().filter(i -> "CONSUMER_TRY".equals(i.type())).findFirst().orElseThrow().value()
        ));
        verify(loanRatesPersistenceService).upsert(eq("TP_KTF10"), eq(LoanRateSubtype.CONSUMER_TRY), eq(LocalDate.of(2026, 5, 8)), eq(new BigDecimal("61.55")));
    }

    @Test
    void latest_skipsNullAndNonPositiveValues() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_CONSUMER_TRY_WEEKLY)).thenReturn("TP_KTF10");
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_VEHICLE_TRY_WEEKLY)).thenReturn(null);
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_HOUSING_TRY_WEEKLY)).thenReturn("");
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_COMMERCIAL_TRY_WEEKLY)).thenReturn("TP_KTF17");
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_KTF10"), any(), any())).thenReturn(List.of(
                new EvdsSeriesPoint(LocalDate.of(2026, 5, 1).atStartOfDay(), null),
                new EvdsSeriesPoint(LocalDate.of(2026, 5, 8).atStartOfDay(), new BigDecimal("-1")),
                new EvdsSeriesPoint(LocalDate.of(2026, 5, 15).atStartOfDay(), new BigDecimal("50"))
        ));
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_KTF17"), any(), any())).thenReturn(List.of());

        LoanRatesLatestResponse r = service.latest();
        assertEquals(1, r.items().size());
        assertEquals("CONSUMER_TRY", r.items().getFirst().type());
    }

    @Test
    void history_filtersDateRange() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_CONSUMER_TRY_WEEKLY)).thenReturn("TP_KTF10");
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_KTF10"), any(), any())).thenReturn(List.of(
                new EvdsSeriesPoint(LocalDate.of(2026, 4, 1).atStartOfDay(), new BigDecimal("59")),
                new EvdsSeriesPoint(LocalDate.of(2026, 5, 8).atStartOfDay(), new BigDecimal("61.55"))
        ));
        LoanRatesHistoryResponse h = service.history(Set.of(LoanRateSubtype.CONSUMER_TRY), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        assertEquals(1, h.series().size());
        assertEquals(1, h.series().getFirst().points().size());
        assertEquals("2026-05-08", h.series().getFirst().points().getFirst().date());
    }
}
