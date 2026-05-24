package com.nurseli.marketdata.application.deposit;

import com.nurseli.marketdata.domain.deposit.DepositRateObservation;
import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.infrastructure.persistence.DepositRateObservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositRatesQueryServiceTest {

    @Mock
    private DepositRatesProperties depositRatesProperties;
    @Mock
    private DepositRateObservationRepository repository;

    @InjectMocks
    private DepositRatesQueryService service;

    @Test
    void findNearestPreviousRate_disabled() {
        when(depositRatesProperties.isEnabled()).thenReturn(false);
        assertTrue(service.findNearestPreviousRate("TRY", "3M", LocalDate.of(2026, 5, 1)).isEmpty());
    }

    @Test
    void findNearestPreviousRate_delegates() {
        when(depositRatesProperties.isEnabled()).thenReturn(true);
        DepositRateObservation obs = new DepositRateObservation();
        obs.setRateValue(new BigDecimal("42"));
        when(repository.findFirstByCurrencyAndTermAndObservationDateLessThanEqualAndRateValueIsNotNullOrderByObservationDateDesc(
                eq("TRY"), eq("3M"), eq(LocalDate.of(2026, 5, 10))
        )).thenReturn(Optional.of(obs));
        Optional<DepositRateObservation> out = service.findNearestPreviousRate("try", "3m", LocalDate.of(2026, 5, 10));
        assertTrue(out.isPresent());
        assertEquals(0, new BigDecimal("42").compareTo(out.get().getRateValue()));
    }
}
