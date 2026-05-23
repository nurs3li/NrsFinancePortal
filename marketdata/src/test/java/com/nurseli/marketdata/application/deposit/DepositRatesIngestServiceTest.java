package com.nurseli.marketdata.application.deposit;

import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.domain.deposit.DepositRateObservation;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import com.nurseli.marketdata.infrastructure.persistence.DepositRateObservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositRatesIngestServiceTest {

    @Mock
    private DepositRatesProperties depositRatesProperties;
    @Mock
    private DepositRatesSeriesResolver depositRatesSeriesResolver;
    @Mock
    private EvdsDebtClient evdsDebtClient;
    @Mock
    private DepositRateObservationRepository repository;

    @InjectMocks
    private DepositRatesIngestService service;

    @Test
    void ingest_disabled() {
        when(depositRatesProperties.isEnabled()).thenReturn(false);
        var r = service.ingestRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1));
        assertEquals(0, r.pointsUpserted());
    }

    @Test
    void upsert_idempotentSecondPass_updatesExisting() {
        when(depositRatesProperties.isEnabled()).thenReturn(true);
        when(depositRatesProperties.getFrequency()).thenReturn("WEEKLY");
        when(depositRatesSeriesResolver.resolved())
                .thenReturn(List.of(new DepositRatesSeriesResolver.ResolvedDepositSeries(
                        "DEPOSIT_RATE_TRY_1M_WEEKLY", "TP_TRY_MT01", "TRY", "1M")));

        LocalDate d = LocalDate.of(2024, 6, 7);
        when(evdsDebtClient.fetchSeriesAscending("TP_TRY_MT01", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                .thenReturn(List.of(new EvdsSeriesPoint(d.atStartOfDay(), new BigDecimal("10.5"))));

        DepositRateObservation existing = new DepositRateObservation();
        existing.setId(99L);
        existing.setSeriesCode("TP_TRY_MT01");
        existing.setObservationDate(d);
        when(repository.findBySeriesCodeAndObservationDate("TP_TRY_MT01", d))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        service.ingestRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        service.ingestRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        verify(repository, times(2)).save(any(DepositRateObservation.class));
        assertEquals(0, new BigDecimal("10.5").compareTo(existing.getRateValue()));
    }
}
