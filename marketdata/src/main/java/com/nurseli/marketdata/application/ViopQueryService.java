package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.ViopContractResponse;
import com.nurseli.marketdata.api.dto.ViopSnapshotResponse;
import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import com.nurseli.marketdata.domain.derivatives.OpenInterestSnapshot;
import com.nurseli.marketdata.repository.DerivativeContractRepository;
import com.nurseli.marketdata.repository.DerivativeSnapshotRepository;
import com.nurseli.marketdata.repository.OpenInterestSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViopQueryService {
    private final DerivativeContractRepository contractRepository;
    private final DerivativeSnapshotRepository snapshotRepository;
    private final OpenInterestSnapshotRepository openInterestSnapshotRepository;

    public List<ViopContractResponse> contracts() {
        return contractRepository.findAll().stream()
                .map(c -> new ViopContractResponse(c.getContractCode(), c.getUnderlying(), c.getExpiry(), c.getType()))
                .toList();
    }

    public List<ViopSnapshotResponse> latest() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        List<ViopSnapshotResponse> rows = snapshotRepository.findAll().stream()
                .filter(s -> !s.getAsOf().isBefore(cutoff))
                .map(this::toSnapshotResponse)
                .toList();
        if (rows.isEmpty()) {
            log.info("[VIOP] latest() returned empty list (no fresh snapshots)");
        }
        return rows;
    }

    public List<ViopSnapshotResponse> history(String contract, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return snapshotRepository.findByContractCodeOrderByAsOfAsc(contract).stream()
                .filter(s -> !s.getAsOf().isBefore(cutoff))
                .map(this::toSnapshotResponse)
                .toList();
    }

    public List<ViopSnapshotResponse> oiHistory(String contract, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return openInterestSnapshotRepository.findByContractCodeOrderByAsOfAsc(contract).stream()
                .filter(oi -> !oi.getAsOf().isBefore(cutoff))
                .map(oi -> {
                    DerivativeSnapshot latestSnapshot = snapshotRepository.findTopByContractCodeOrderByAsOfDesc(contract).orElse(null);
                    BigDecimal spot = latestSnapshot != null ? latestSnapshot.getTheoreticalSpot() : BigDecimal.ONE;
                    BigDecimal price = latestSnapshot != null ? latestSnapshot.getPrice() : BigDecimal.ONE;
                    return build(contract, price, spot, oi.getOpenInterest(), oi.getAsOf(), latestSnapshot != null ? latestSnapshot.getSource() : "VIOP");
                })
                .toList();
    }

    private ViopSnapshotResponse toSnapshotResponse(DerivativeSnapshot s) {
        Long oi = openInterestSnapshotRepository.findTopByContractCodeOrderByAsOfDesc(s.getContractCode())
                .map(OpenInterestSnapshot::getOpenInterest)
                .orElse(0L);
        String source = (s.getSource() == null || s.getSource().isBlank()) ? "VIOP_MVP" : s.getSource();
        LocalDateTime asOf = s.getAsOf() == null ? LocalDateTime.now() : s.getAsOf();
        return build(s.getContractCode(), s.getPrice(), s.getTheoreticalSpot(), oi, asOf, source);
    }

    private ViopSnapshotResponse build(
            String contractCode, BigDecimal price, BigDecimal spot, Long oi, LocalDateTime asOf, String source
    ) {
        BigDecimal safeSpot = spot == null || spot.signum() == 0 ? BigDecimal.ONE : spot;
        BigDecimal basis = price.subtract(safeSpot);
        BigDecimal annualized = basis.divide(safeSpot, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("36500"))
                .divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
        String regime = basis.signum() >= 0 && oi > 0 ? "PRICE_UP_OI_UP" : "NEUTRAL";
        return new ViopSnapshotResponse(contractCode, price, safeSpot, basis, annualized, oi, regime, source, asOf);
    }
}
