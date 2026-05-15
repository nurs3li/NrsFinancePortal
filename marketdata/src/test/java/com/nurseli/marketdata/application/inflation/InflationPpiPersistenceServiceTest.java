package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.domain.inflation.InflationIndexMonthlyEntity;
import com.nurseli.marketdata.repository.InflationIndexMonthlyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InflationPpiPersistenceServiceTest {

    @Mock
    private InflationIndexMonthlyRepository repository;

    @InjectMocks
    private InflationPpiPersistenceService persistenceService;

    @Test
    void upsertPpiMonth_idempotentUpdatesSameRow() {
        String series = "TP_TUFE1YI_T1";
        LocalDate month = YearMonth.of(2025, 4).atDay(1);
        InflationMonthMetrics m1 = new InflationMonthMetrics(
                YearMonth.of(2025, 4),
                new BigDecimal("500"),
                new BigDecimal("1.1"),
                new BigDecimal("10.2")
        );
        InflationIndexMonthlyEntity existing = new InflationIndexMonthlyEntity();
        existing.setId(42L);
        existing.setCreatedAt(LocalDateTime.parse("2025-05-01T10:00:00"));
        when(repository.findByIndicatorTypeAndSeriesCodeAndObservationMonth(
                InflationIndicatorType.PPI, series, month
        )).thenReturn(Optional.of(existing));

        persistenceService.upsertPpiMonth(series, 2003, m1);

        ArgumentCaptor<InflationIndexMonthlyEntity> cap = ArgumentCaptor.forClass(InflationIndexMonthlyEntity.class);
        verify(repository).save(cap.capture());
        InflationIndexMonthlyEntity saved = cap.getValue();
        assertEquals(42L, saved.getId());
        assertEquals(new BigDecimal("500"), saved.getIndexValue());
        assertNotNull(saved.getUpdatedAt());

        InflationMonthMetrics m2 = new InflationMonthMetrics(
                YearMonth.of(2025, 4),
                new BigDecimal("501"),
                new BigDecimal("1.2"),
                new BigDecimal("10.3")
        );
        persistenceService.upsertPpiMonth(series, 2003, m2);
        verify(repository, times(2)).save(cap.capture());
        assertEquals(new BigDecimal("501"), cap.getValue().getIndexValue());
    }
}
