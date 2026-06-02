package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.ingest.DebtHistoryWarmupService;
import com.nurseli.marketdata.application.ingest.DebtIngestService;
import com.nurseli.marketdata.api.dto.DebtHistoryCoverageResponse;
import com.nurseli.marketdata.api.dto.DebtHistoryWarmupResponse;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import com.nurseli.marketdata.infrastructure.persistence.DebtSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtHistoryWarmupServiceTest {

    @Mock
    private DebtSnapshotRepository debtSnapshotRepository;
    @Mock
    private DebtIngestService debtIngestService;

    private DebtHistoryWarmupService service;

    @BeforeEach
    void setUp() {
        EvdsProperties properties = new EvdsProperties();
        properties.setEnabled(true);
        properties.getDebt().setEnabled(true);
        EvdsProperties.Instrument instrument = new EvdsProperties.Instrument();
        instrument.setIsin("TRT170227K64");
        properties.getDebt().setInstruments(List.of(instrument));
        service = new DebtHistoryWarmupService(properties, debtSnapshotRepository, debtIngestService);
    }

    @Test
    void coverage_readyWhenRangeFullyCovered() {
        when(debtSnapshotRepository.findTopByIsinOrderByAsOfAsc("TRT170227K64"))
                .thenReturn(Optional.of(snapshot(LocalDate.of(2026, 5, 1))));
        when(debtSnapshotRepository.findTopByIsinOrderByAsOfDesc("TRT170227K64"))
                .thenReturn(Optional.of(snapshot(LocalDate.of(2026, 5, 5))));
        when(debtSnapshotRepository.countDistinctObservationDays(eq("TRT170227K64"), any(), any())).thenReturn(5L);

        DebtHistoryCoverageResponse coverage =
                service.getCoverage("TRT170227K64", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

        assertThat(coverage.ready()).isTrue();
        assertThat(coverage.availableDays()).isEqualTo(5);
    }

    @Test
    void requestWarmup_returnsReadyWhenCoverageAlreadyExists() {
        when(debtSnapshotRepository.findTopByIsinOrderByAsOfAsc("TRT170227K64"))
                .thenReturn(Optional.of(snapshot(LocalDate.of(2026, 5, 1))));
        when(debtSnapshotRepository.findTopByIsinOrderByAsOfDesc("TRT170227K64"))
                .thenReturn(Optional.of(snapshot(LocalDate.of(2026, 5, 5))));
        when(debtSnapshotRepository.countDistinctObservationDays(eq("TRT170227K64"), any(), any())).thenReturn(5L);

        DebtHistoryWarmupResponse response =
                service.requestWarmup(List.of("TRT170227K64"), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), "test");

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.queuedIsins()).isZero();
    }

    @Test
    void warmupSync_splitsLongRangeIntoChunks() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(220);
        LocalDate to = today;
        when(debtSnapshotRepository.findTopByIsinOrderByAsOfAsc("TRT170227K64")).thenReturn(Optional.empty());
        when(debtSnapshotRepository.findTopByIsinOrderByAsOfDesc("TRT170227K64")).thenReturn(Optional.empty());
        when(debtSnapshotRepository.countDistinctObservationDays(eq("TRT170227K64"), any(), any())).thenReturn(0L);

        service.warmupSync("TRT170227K64", from, to, "test");

        verify(debtIngestService).ingestHistoryRange("TRT170227K64", from, from.plusDays(179), "test");
        verify(debtIngestService).ingestHistoryRange("TRT170227K64", from.plusDays(180), to, "test");
    }

    @Test
    void requestWarmupIfMissing_queuesAsyncFill() {
        when(debtSnapshotRepository.findTopByIsinOrderByAsOfAsc("TRT170227K64")).thenReturn(Optional.empty());
        when(debtSnapshotRepository.findTopByIsinOrderByAsOfDesc("TRT170227K64")).thenReturn(Optional.empty());
        when(debtSnapshotRepository.countDistinctObservationDays(eq("TRT170227K64"), any(), any())).thenReturn(0L);

        service.requestWarmupIfMissing("TRT170227K64", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), "read-history");

        verify(debtIngestService, timeout(1000))
                .ingestHistoryRange("TRT170227K64", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), "read-history");
    }

    private static DebtSnapshot snapshot(LocalDate day) {
        DebtSnapshot snapshot = new DebtSnapshot();
        snapshot.setAsOf(LocalDateTime.of(day, java.time.LocalTime.MIDNIGHT));
        return snapshot;
    }
}
