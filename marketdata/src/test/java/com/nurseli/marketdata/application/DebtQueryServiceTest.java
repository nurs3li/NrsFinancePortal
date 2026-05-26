package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.domain.debt.DebtInstrument;
import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import com.nurseli.marketdata.infrastructure.persistence.DebtInstrumentRepository;
import com.nurseli.marketdata.infrastructure.persistence.DebtSnapshotRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtQueryServiceTest {

  @Mock private DebtInstrumentRepository debtInstrumentRepository;
  @Mock private DebtSnapshotRepository debtSnapshotRepository;
  @Mock private DebtHistoryWarmupService debtHistoryWarmupService;
  @Mock private MarketStaleTailRepairService marketStaleTailRepairService;

  @InjectMocks private DebtQueryService service;

  @Test
  void latest_filtersSyntheticWhenExactSnapshotExists() {
    DebtInstrument instrument = instrument("TR000001", "2030-12-31");
    when(debtInstrumentRepository.findAll()).thenReturn(List.of(instrument));
    when(debtSnapshotRepository.findLatestPerIsinSince(any()))
        .thenReturn(
            List.of(
                snapshot("TR000001", "EVDS", new BigDecimal("101.2"), LocalDateTime.now().minusHours(2)),
                snapshot(
                    "TR000001",
                    "DEBT_MVP",
                    new BigDecimal("100.0"),
                    LocalDateTime.now().minusHours(1))));

    List<DebtSnapshotResponse> rows = service.latest();

    assertEquals(1, rows.size());
    assertEquals("EXACT", rows.getFirst().quality());
    assertEquals(false, rows.getFirst().synthetic());
  }

  @Test
  void latest_returnsEmptyWhenNoFreshSnapshots() {
    when(debtInstrumentRepository.findAll()).thenReturn(List.of());
    when(debtSnapshotRepository.findLatestPerIsinSince(any())).thenReturn(List.of());

    assertTrue(service.latest().isEmpty());
  }

  @Test
  void history_blankIsin_returnsEmpty() {
    assertTrue(service.history(" ", 30).isEmpty());
  }

  @Test
  void history_mapsEvdsCouponRateAndNormalizesIsin() {
    DebtInstrument instrument = instrument("tr000001", "2030-12-31");
    when(debtInstrumentRepository.findByIsin("TR000001")).thenReturn(Optional.of(instrument));
    LocalDateTime older = LocalDateTime.now().minusDays(5);
    LocalDateTime newer = LocalDateTime.now().minusDays(2);
    when(debtSnapshotRepository.findByIsinAndAsOfGreaterThanEqualOrderByAsOfAsc(eq("TR000001"), any()))
        .thenReturn(
            List.of(
                snapshot("TR000001", "EVDS", new BigDecimal("99.5"), older),
                snapshot("TR000001", "EVDS", new BigDecimal("99.8"), newer)));

    List<DebtSnapshotResponse> rows = service.history("tr000001", 9999);

    assertEquals(2, rows.size());
    DebtSnapshotResponse latest = rows.get(1);
    assertEquals(new BigDecimal("99.8"), latest.dirtyPrice());
    assertEquals(new BigDecimal("12.5"), latest.couponRate());
    assertEquals("EXACT", latest.quality());
    assertNotNull(latest.daysToMaturity());
  }

  private static DebtInstrument instrument(String isin, String maturity) {
    DebtInstrument instrument = new DebtInstrument();
    instrument.setIsin(isin);
    instrument.setName("Test Bond");
    instrument.setIssuer("Treasury");
    instrument.setMaturityDate(maturity);
    return instrument;
  }

  private static DebtSnapshot snapshot(
      String isin, String source, BigDecimal price, LocalDateTime asOf) {
    DebtSnapshot snapshot = new DebtSnapshot();
    snapshot.setIsin(isin);
    snapshot.setSource(source);
    snapshot.setDirtyPrice(price);
    snapshot.setYieldPct(new BigDecimal("12.5"));
    snapshot.setAsOf(asOf);
    return snapshot;
  }
}
