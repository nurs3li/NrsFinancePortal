package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InflationPpiPersistenceServiceTest {

    @Mock
    private InflationIndexPersistenceService inflationIndexPersistenceService;

    @InjectMocks
    private InflationPpiPersistenceService persistenceService;

    @Test
    void upsertPpiMonth_delegatesToIndexPersistence() {
        String series = "TP_TUFE1YI_T1";
        InflationMonthMetrics m1 = new InflationMonthMetrics(
                YearMonth.of(2025, 4),
                new BigDecimal("500"),
                new BigDecimal("1.1"),
                new BigDecimal("10.2")
        );

        persistenceService.upsertPpiMonth(series, 2003, m1);

        ArgumentCaptor<InflationIndicatorType> typeCap = ArgumentCaptor.forClass(InflationIndicatorType.class);
        verify(inflationIndexPersistenceService).upsertMonth(typeCap.capture(), org.mockito.ArgumentMatchers.eq(series), org.mockito.ArgumentMatchers.eq(2003), org.mockito.ArgumentMatchers.eq(m1));
        assertEquals(InflationIndicatorType.PPI, typeCap.getValue());

        InflationMonthMetrics m2 = new InflationMonthMetrics(
                YearMonth.of(2025, 4),
                new BigDecimal("501"),
                new BigDecimal("1.2"),
                new BigDecimal("10.3")
        );
        persistenceService.upsertPpiMonth(series, 2003, m2);
        verify(inflationIndexPersistenceService, times(2)).upsertMonth(
                org.mockito.ArgumentMatchers.eq(InflationIndicatorType.PPI),
                org.mockito.ArgumentMatchers.eq(series),
                org.mockito.ArgumentMatchers.eq(2003),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
