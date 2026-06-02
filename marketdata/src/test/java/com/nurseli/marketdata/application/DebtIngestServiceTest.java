package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.ingest.DebtIngestService;
import com.nurseli.marketdata.application.debt.DebtCouponFrequencyPersistence;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.domain.debt.DebtInstrument;
import com.nurseli.marketdata.infrastructure.debt.DebtMarketClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.persistence.DebtInstrumentRepository;
import com.nurseli.marketdata.infrastructure.persistence.DebtSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtIngestServiceTest {

    @Mock
    private DebtInstrumentRepository debtInstrumentRepository;
    @Mock
    private DebtSnapshotRepository debtSnapshotRepository;
    @Mock
    private DebtMarketClient debtMarketClient;
    @Mock
    private EvdsDebtClient evdsDebtClient;
    @Mock
    private DebtCouponFrequencyPersistence debtCouponFrequencyPersistence;

    private EvdsProperties evdsProperties;
    private DebtIngestService service;

    @BeforeEach
    void setUp() {
        evdsProperties = new EvdsProperties();
        evdsProperties.setEnabled(true);
        evdsProperties.getDebt().setEnabled(true);
        EvdsProperties.Instrument instrument = new EvdsProperties.Instrument();
        instrument.setIsin("TRT170227K64");
        instrument.setName("TRT170227K64");
        instrument.setIssuer("Hazine");
        instrument.setMaturityDate("2027-02-17");
        evdsProperties.getDebt().setInstruments(List.of(instrument));
        service = new DebtIngestService(
                debtInstrumentRepository,
                debtSnapshotRepository,
                debtMarketClient,
                evdsDebtClient,
                debtCouponFrequencyPersistence,
                evdsProperties);
        when(debtInstrumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ingestLatest_skipsDuplicateSnapshotForSameIsinSourceAndAsOf() {
        DebtInstrument instrument = instrument("TRT170227K64");
        LocalDateTime asOf = LocalDate.of(2026, 5, 26).atStartOfDay();
        when(debtMarketClient.fetchLatest()).thenReturn(List.of());
        when(evdsDebtClient.fetchLatest()).thenReturn(List.of(
                new EvdsDebtClient.EvdsDebtRow(
                        "TRT170227K64",
                        "TRT170227K64",
                        "Hazine",
                        "2027-02-17",
                        new BigDecimal("14.846"),
                        new BigDecimal("17.2"),
                        asOf,
                        "EVDS")));
        when(debtInstrumentRepository.findByIsin("TRT170227K64")).thenReturn(Optional.of(instrument));
        when(debtSnapshotRepository.existsByIsinAndSourceAndAsOf("TRT170227K64", "EVDS", asOf)).thenReturn(true);

        service.ingestLatest();

        verify(debtSnapshotRepository, never()).save(any());
    }

    @Test
    void ingestHistoryRange_persistsMissingRowsForConfiguredInstrument() {
        EvdsProperties.Instrument configured = evdsProperties.getDebt().getInstruments().getFirst();
        DebtInstrument instrument = instrument("TRT170227K64");
        LocalDate from = LocalDate.of(2026, 5, 23);
        LocalDate to = LocalDate.of(2026, 5, 24);
        LocalDateTime d1 = from.atStartOfDay();
        LocalDateTime d2 = to.atStartOfDay();
        when(evdsDebtClient.fetchInstrumentHistory(configured, from, to)).thenReturn(List.of(
                new EvdsDebtClient.EvdsDebtRow("TRT170227K64", "TRT170227K64", "Hazine", "2027-02-17", new BigDecimal("14.667"), new BigDecimal("17.2"), d1, "EVDS"),
                new EvdsDebtClient.EvdsDebtRow("TRT170227K64", "TRT170227K64", "Hazine", "2027-02-17", new BigDecimal("14.683"), new BigDecimal("17.2"), d2, "EVDS")));
        when(debtInstrumentRepository.findByIsin("TRT170227K64")).thenReturn(Optional.of(instrument));
        when(debtSnapshotRepository.existsByIsinAndSourceAndAsOf(eq("TRT170227K64"), eq("EVDS"), any())).thenReturn(false);

        int inserted = service.ingestHistoryRange("TRT170227K64", from, to, "test");

        assertThat(inserted).isEqualTo(2);
        verify(debtSnapshotRepository, times(2)).save(any());
    }

    private static DebtInstrument instrument(String isin) {
        DebtInstrument instrument = new DebtInstrument();
        instrument.setIsin(isin);
        instrument.setName(isin);
        instrument.setIssuer("Hazine");
        instrument.setMaturityDate("2027-02-17");
        return instrument;
    }
}
