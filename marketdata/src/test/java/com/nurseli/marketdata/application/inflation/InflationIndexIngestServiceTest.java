package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.config.InflationCpiProperties;
import com.nurseli.marketdata.config.InflationPpiProperties;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InflationIndexIngestServiceTest {

    @Mock
    private EvdsProperties evdsProperties;
    @Mock
    private InflationCpiProperties inflationCpiProperties;
    @Mock
    private InflationPpiProperties inflationPpiProperties;
    @Mock
    private EvdsDebtClient evdsDebtClient;
    @Mock
    private InflationIndexPersistenceService persistenceService;
    @Mock
    private InflationIndexQueryService queryService;
    @Mock
    private InflationMacroCacheService inflationMacroCacheService;

    @InjectMocks
    private InflationIndexIngestService ingestService;

    @Test
    void backfill_upsertsCpiAndEvictsCache() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        when(inflationCpiProperties.isPersistEnabled()).thenReturn(true);
        when(inflationCpiProperties.getBaseYear()).thenReturn(2003);
        when(evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.CPI_TR_INDEX)).thenReturn("TP_GENENDEKS_T1");

        LocalDate d = LocalDate.of(2026, 3, 1);
        when(evdsDebtClient.fetchSeriesAscending(eq("TP_GENENDEKS_T1"), any(), any()))
                .thenReturn(List.of(new EvdsSeriesPoint(LocalDateTime.of(2026, 3, 3, 0, 0), new BigDecimal("4200"))));

        when(queryService.summarize(InflationIndicatorType.CPI))
                .thenReturn(new InflationIndexQueryService.SeriesSummary(
                        "CPI",
                        "TP_GENENDEKS_T1",
                        d,
                        new BigDecimal("4200"),
                        1
                ));

        var r = ingestService.backfill(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 19), true, false);

        assertEquals("ok", r.status());
        assertEquals(1, r.series().size());
        assertEquals("TP_GENENDEKS_T1", r.series().getFirst().code());
        verify(persistenceService).upsertMonth(eq(InflationIndicatorType.CPI), eq("TP_GENENDEKS_T1"), eq(2003), any());
        verify(inflationMacroCacheService).evictInflationCaches();
    }
}
