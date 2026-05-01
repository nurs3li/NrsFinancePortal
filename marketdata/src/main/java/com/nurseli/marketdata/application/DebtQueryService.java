package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.DebtInstrumentResponse;
import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.repository.DebtInstrumentRepository;
import com.nurseli.marketdata.repository.DebtSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtQueryService {
    private final DebtInstrumentRepository debtInstrumentRepository;
    private final DebtSnapshotRepository debtSnapshotRepository;
    private static final DateTimeFormatter[] MATURITY_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    public List<DebtInstrumentResponse> catalog() {
        return debtInstrumentRepository.findAll().stream()
                .map(i -> new DebtInstrumentResponse(i.getIsin(), i.getName(), i.getIssuer(), i.getMaturityDate()))
                .toList();
    }

    public List<DebtSnapshotResponse> latest() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        List<DebtSnapshotResponse> rows = debtSnapshotRepository.findAll().stream()
                .filter(s -> !s.getAsOf().isBefore(cutoff))
                .collect(Collectors.toMap(
                        com.nurseli.marketdata.domain.debt.DebtSnapshot::getIsin,
                        s -> s,
                        (left, right) -> {
                            LocalDateTime leftAsOf = left.getAsOf() == null ? LocalDateTime.MIN : left.getAsOf();
                            LocalDateTime rightAsOf = right.getAsOf() == null ? LocalDateTime.MIN : right.getAsOf();
                            if (rightAsOf.isAfter(leftAsOf)) {
                                return right;
                            }
                            if (leftAsOf.isAfter(rightAsOf)) {
                                return left;
                            }
                            Long leftId = left.getId() == null ? Long.MIN_VALUE : left.getId();
                            Long rightId = right.getId() == null ? Long.MIN_VALUE : right.getId();
                            return rightId > leftId ? right : left;
                        }
                ))
                .values().stream()
                .sorted(Comparator.comparing(
                        com.nurseli.marketdata.domain.debt.DebtSnapshot::getAsOf,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::toResponse)
                .toList();
        if (rows.isEmpty()) {
            log.info("[DEBT] latest() returned empty list (no fresh snapshots)");
            return rows;
        }
        boolean hasExact = rows.stream().anyMatch(r -> !Boolean.TRUE.equals(r.synthetic()));
        if (!hasExact) {
            return rows;
        }
        return rows.stream()
                .filter(r -> !Boolean.TRUE.equals(r.synthetic()))
                .toList();
    }

    public List<DebtSnapshotResponse> history(String isin, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return debtSnapshotRepository.findByIsinOrderByAsOfAsc(isin).stream()
                .filter(s -> !s.getAsOf().isBefore(cutoff))
                .collect(Collectors.toMap(
                        s -> s.getAsOf() == null ? LocalDateTime.MIN : s.getAsOf(),
                        s -> s,
                        (left, right) -> {
                            Long leftId = left.getId() == null ? Long.MIN_VALUE : left.getId();
                            Long rightId = right.getId() == null ? Long.MIN_VALUE : right.getId();
                            return rightId > leftId ? right : left;
                        }
                ))
                .values().stream()
                .sorted(Comparator.comparing(
                        com.nurseli.marketdata.domain.debt.DebtSnapshot::getAsOf,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .map(this::toResponse)
                .toList();
    }

    private DebtSnapshotResponse toResponse(com.nurseli.marketdata.domain.debt.DebtSnapshot s) {
        String maturityDate = debtInstrumentRepository.findByIsin(s.getIsin())
                .map(com.nurseli.marketdata.domain.debt.DebtInstrument::getMaturityDate)
                .orElse(null);
        String source = (s.getSource() == null || s.getSource().isBlank()) ? "DEBT_MVP" : s.getSource();
        LocalDateTime asOf = s.getAsOf() == null ? LocalDateTime.now() : s.getAsOf();
        boolean synthetic = source.toUpperCase().contains("MVP");
        String quality = synthetic ? "FALLBACK" : "EXACT";
        Long daysToMaturity = calculateDaysToMaturity(maturityDate);
        BigDecimal couponRate = inferCouponRateFromYield(s.getYieldPct());
        return new DebtSnapshotResponse(
                s.getIsin(),
                s.getDirtyPrice(),
                s.getYieldPct(),
                maturityDate,
                daysToMaturity,
                couponRate,
                source,
                asOf,
                quality,
                synthetic
        );
    }

    private Long calculateDaysToMaturity(String maturityDate) {
        if (maturityDate == null || maturityDate.isBlank()) return null;
        LocalDate now = LocalDate.now();
        for (DateTimeFormatter fmt : MATURITY_FORMATS) {
            try {
                LocalDate m = LocalDate.parse(maturityDate, fmt);
                return ChronoUnit.DAYS.between(now, m);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Coupon rate provider yoksa geçici olarak yield bazlı güvenli fallback döner.
     */
    private BigDecimal inferCouponRateFromYield(BigDecimal yieldPct) {
        if (yieldPct == null) return BigDecimal.ZERO;
        return yieldPct.max(BigDecimal.ZERO);
    }
}
