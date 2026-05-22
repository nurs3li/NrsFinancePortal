package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.DebtInstrumentResponse;
import com.nurseli.marketdata.api.dto.DebtSnapshotResponse;
import com.nurseli.marketdata.domain.debt.DebtInstrument;
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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtQueryService {
    private final DebtInstrumentRepository debtInstrumentRepository;
    private final DebtSnapshotRepository debtSnapshotRepository;
    private final MarketStaleTailRepairService marketStaleTailRepairService;
    private static final DateTimeFormatter[] MATURITY_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    public List<DebtInstrumentResponse> catalog() {
        return debtInstrumentRepository.findAll().stream().map(this::toInstrumentResponse).toList();
    }

    public List<DebtSnapshotResponse> latest() {
        marketStaleTailRepairService.repairDebtSnapshotsIfStale();
        Map<String, DebtInstrument> instruments = instrumentsByIsin();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        List<DebtSnapshotResponse> rows = debtSnapshotRepository.findAll().stream()
                .filter(s -> !s.getAsOf().isBefore(cutoff))
                .collect(Collectors.toMap(
                        com.nurseli.marketdata.domain.debt.DebtSnapshot::getIsin,
                        s -> s,
                        DebtQueryService::mergeDebtSnapshots
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(
                        com.nurseli.marketdata.domain.debt.DebtSnapshot::getAsOf,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> toResponse(s, instruments.get(normIsin(s.getIsin()))))
                .toList();
        if (rows.isEmpty()) {
            log.info("[DEBT] latest() returned empty list (no fresh snapshots)");
            return rows;
        }
        boolean hasExact = rows.stream().anyMatch(r -> !Boolean.TRUE.equals(r.synthetic()));
        if (!hasExact) {
            return rows;
        }
        return rows.stream().filter(r -> !Boolean.TRUE.equals(r.synthetic())).toList();
    }

    public List<DebtSnapshotResponse> history(String isin, int days) {
        if (isin == null || isin.isBlank()) {
            return List.of();
        }
        String key = normIsin(isin);
        int safeDays = Math.max(1, Math.min(days, 800));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeDays);
        DebtInstrument instrument = debtInstrumentRepository.findByIsin(key).orElse(null);
        String maturityDate = instrument != null ? instrument.getMaturityDate() : null;
        return debtSnapshotRepository.findByIsinAndAsOfGreaterThanEqualOrderByAsOfAsc(key, cutoff).stream()
                .collect(Collectors.toMap(
                        s -> s.getAsOf() == null ? LocalDateTime.MIN : s.getAsOf(),
                        s -> s,
                        (left, right) -> {
                            Long leftId = left.getId() == null ? Long.MIN_VALUE : left.getId();
                            Long rightId = right.getId() == null ? Long.MIN_VALUE : right.getId();
                            return rightId > leftId ? right : left;
                        }))
                .values()
                .stream()
                .sorted(Comparator.comparing(
                        com.nurseli.marketdata.domain.debt.DebtSnapshot::getAsOf,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(s -> toResponse(s, instrument))
                .toList();
    }

    private Map<String, DebtInstrument> instrumentsByIsin() {
        return debtInstrumentRepository.findAll().stream()
                .filter(i -> i.getIsin() != null && !i.getIsin().isBlank())
                .collect(Collectors.toMap(i -> normIsin(i.getIsin()), i -> i, (a, b) -> a));
    }

    private static String normIsin(String isin) {
        return isin == null ? "" : isin.trim().toUpperCase(Locale.ROOT);
    }

    private static com.nurseli.marketdata.domain.debt.DebtSnapshot mergeDebtSnapshots(
            com.nurseli.marketdata.domain.debt.DebtSnapshot left,
            com.nurseli.marketdata.domain.debt.DebtSnapshot right) {
        com.nurseli.marketdata.domain.debt.DebtSnapshot newer = snapshotIsNewer(left, right) ? left : right;
        com.nurseli.marketdata.domain.debt.DebtSnapshot older = newer == left ? right : left;
        if (snapshotCouponRate(older) != null && snapshotCouponRate(newer) == null) {
            newer.setYieldPct(older.getYieldPct());
        }
        if (older.getDirtyPrice() != null
                && older.getDirtyPrice().signum() > 0
                && (newer.getDirtyPrice() == null || newer.getDirtyPrice().signum() <= 0)) {
            newer.setDirtyPrice(older.getDirtyPrice());
        }
        return newer;
    }

    private static boolean snapshotIsNewer(
            com.nurseli.marketdata.domain.debt.DebtSnapshot left,
            com.nurseli.marketdata.domain.debt.DebtSnapshot right) {
        LocalDateTime leftAsOf = left.getAsOf() == null ? LocalDateTime.MIN : left.getAsOf();
        LocalDateTime rightAsOf = right.getAsOf() == null ? LocalDateTime.MIN : right.getAsOf();
        if (rightAsOf.isAfter(leftAsOf)) {
            return false;
        }
        if (leftAsOf.isAfter(rightAsOf)) {
            return true;
        }
        Long leftId = left.getId() == null ? Long.MIN_VALUE : left.getId();
        Long rightId = right.getId() == null ? Long.MIN_VALUE : right.getId();
        return leftId >= rightId;
    }

    private static BigDecimal snapshotCouponRate(com.nurseli.marketdata.domain.debt.DebtSnapshot s) {
        if (s == null || s.getYieldPct() == null || s.getYieldPct().signum() <= 0) {
            return null;
        }
        String source = s.getSource() == null ? "" : s.getSource().toUpperCase(Locale.ROOT);
        if (source.contains("EVDS") && !source.contains("MVP")) {
            return s.getYieldPct();
        }
        return null;
    }

    private DebtInstrumentResponse toInstrumentResponse(DebtInstrument i) {
        return new DebtInstrumentResponse(
                i.getIsin(),
                i.getName(),
                i.getIssuer(),
                i.getMaturityDate(),
                i.getCouponFrequencyPerYear(),
                i.getCouponFrequencyLabel(),
                i.getCouponFrequencySource());
    }

    private DebtSnapshotResponse toResponse(
            com.nurseli.marketdata.domain.debt.DebtSnapshot s, DebtInstrument instrument) {
        String maturityDate = instrument != null ? instrument.getMaturityDate() : null;
        String source = (s.getSource() == null || s.getSource().isBlank()) ? "DEBT_MVP" : s.getSource();
        LocalDateTime asOf = s.getAsOf() == null ? LocalDateTime.now() : s.getAsOf();
        boolean synthetic = source.toUpperCase(Locale.ROOT).contains("MVP");
        String quality = synthetic ? "FALLBACK" : "EXACT";
        Long daysToMaturity = calculateDaysToMaturity(maturityDate);
        BigDecimal couponRate = resolveCouponRate(s, source, synthetic);
        Integer freqYear = instrument != null ? instrument.getCouponFrequencyPerYear() : null;
        String freqLabel = instrument != null ? instrument.getCouponFrequencyLabel() : null;
        String freqSource = instrument != null ? instrument.getCouponFrequencySource() : null;
        return new DebtSnapshotResponse(
                s.getIsin(),
                s.getDirtyPrice(),
                null,
                maturityDate,
                daysToMaturity,
                couponRate,
                source,
                asOf,
                quality,
                synthetic,
                Boolean.FALSE,
                "BOND_PRICE_PERFORMANCE",
                "PRICE",
                Boolean.FALSE,
                freqYear,
                freqLabel,
                freqSource);
    }

    /** EVDS kupon faiz oranı DB'de yieldPct sütununda; API'de yalnızca couponRate olarak döner. */
    private static BigDecimal resolveCouponRate(
            com.nurseli.marketdata.domain.debt.DebtSnapshot s, String source, boolean synthetic) {
        if (s.getYieldPct() == null || s.getYieldPct().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (synthetic) {
            return null;
        }
        if (source != null && source.toUpperCase(Locale.ROOT).contains("EVDS")) {
            return s.getYieldPct();
        }
        return null;
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
}
