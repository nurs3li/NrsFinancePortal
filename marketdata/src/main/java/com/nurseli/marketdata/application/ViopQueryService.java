package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.ViopContractResponse;
import com.nurseli.marketdata.api.dto.ViopMarketWatchResponse;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViopQueryService {
    private final DerivativeContractRepository contractRepository;
    private final DerivativeSnapshotRepository snapshotRepository;
    private final OpenInterestSnapshotRepository openInterestSnapshotRepository;
    private final ViopContractParser viopContractParser;

    public List<ViopContractResponse> contracts() {
        return contractRepository.findAll().stream()
                .collect(Collectors.toMap(
                        c -> viopContractParser.normalizeContractCode(c.getContractCode()),
                        c -> c,
                        (left, right) -> right
                ))
                .values().stream()
                .map(c -> new ViopContractResponse(
                        viopContractParser.normalizeContractCode(c.getContractCode()),
                        c.getUnderlying(),
                        c.getExpiry(),
                        c.getType()
                ))
                .toList();
    }

    public List<ViopSnapshotResponse> latest() {
        List<ViopSnapshotResponse> rows = snapshotRepository.findLatestSnapshotPerContract().stream()
                .collect(Collectors.toMap(
                        s -> viopContractParser.normalizeContractCode(s.getContractCode()),
                        s -> s,
                        (left, right) -> {
                            LocalDateTime leftAsOf = left.getAsOf() == null ? LocalDateTime.MIN : left.getAsOf();
                            LocalDateTime rightAsOf = right.getAsOf() == null ? LocalDateTime.MIN : right.getAsOf();
                            return rightAsOf.isAfter(leftAsOf) ? right : left;
                        }
                ))
                .values().stream()
                .map(this::toSnapshotResponse)
                .toList();
        if (rows.isEmpty()) {
            log.info("[VIOP] latest() returned empty list (no fresh snapshots)");
        }
        return rows;
    }

    public List<ViopSnapshotResponse> history(String contract, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return loadSnapshotsByAlias(contract).stream()
                .filter(s -> !s.getAsOf().isBefore(cutoff))
                .map(this::toSnapshotResponse)
                .toList();
    }

    public List<ViopSnapshotResponse> oiHistory(String contract, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return loadOiByAlias(contract).stream()
                .filter(oi -> !oi.getAsOf().isBefore(cutoff))
                .map(oi -> {
                    DerivativeSnapshot latestSnapshot = findLatestByAlias(contract);
                    BigDecimal spot = latestSnapshot != null ? latestSnapshot.getTheoreticalSpot() : BigDecimal.ONE;
                    BigDecimal price = latestSnapshot != null ? latestSnapshot.getPrice() : BigDecimal.ONE;
                    return build(
                            viopContractParser.normalizeContractCode(contract),
                            price,
                            spot,
                            oi.getOpenInterest(),
                            oi.getDailyVolume(),
                            oi.getAsOf(),
                            latestSnapshot != null ? latestSnapshot.getSource() : "VIOP",
                            latestSnapshot != null ? latestSnapshot.getBasis() : null,
                            latestSnapshot != null ? latestSnapshot.getMaintenanceMargin() : null,
                            latestSnapshot != null ? latestSnapshot.getDaysToExpiry() : null,
                            latestSnapshot != null ? latestSnapshot.getDataQuality() : null,
                            latestSnapshot != null ? latestSnapshot.getPriceSource() : null,
                            latestSnapshot != null ? latestSnapshot.getPriceLatencyMs() : null
                    );
                })
                .toList();
    }

    public ViopMarketWatchResponse marketWatch() {
        List<ViopSnapshotResponse> latest = latest();
        List<ViopMarketWatchResponse.Leader> topGainers = latest.stream()
                .sorted(Comparator.comparing(ViopSnapshotResponse::annualizedBasisPct, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(this::toLeader)
                .toList();
        List<ViopMarketWatchResponse.Leader> volumeLeaders = latest.stream()
                .sorted((a, b) -> Long.compare(nullSafeLong(b.dailyVolume()), nullSafeLong(a.dailyVolume())))
                .limit(5)
                .map(this::toLeader)
                .toList();
        List<ViopMarketWatchResponse.Leader> oiSurgeLeaders = latest.stream()
                .sorted((a, b) -> Long.compare(nullSafeLong(b.openInterest()), nullSafeLong(a.openInterest())))
                .limit(5)
                .map(this::toLeader)
                .toList();
        return new ViopMarketWatchResponse(topGainers, volumeLeaders, oiSurgeLeaders, LocalDateTime.now());
    }

    private ViopSnapshotResponse toSnapshotResponse(DerivativeSnapshot s) {
        OpenInterestSnapshot latestOi = findLatestOiByAlias(s.getContractCode());
        Long oi = latestOi != null ? latestOi.getOpenInterest() : 0L;
        Long dailyVolume = latestOi != null ? latestOi.getDailyVolume() : null;
        String source = (s.getSource() == null || s.getSource().isBlank()) ? "VIOP_MVP" : s.getSource();
        LocalDateTime asOf = s.getAsOf() == null ? LocalDateTime.now() : s.getAsOf();
        return build(
                viopContractParser.normalizeContractCode(s.getContractCode()),
                s.getPrice(),
                s.getTheoreticalSpot(),
                oi,
                dailyVolume,
                asOf,
                source,
                s.getBasis(),
                s.getMaintenanceMargin(),
                s.getDaysToExpiry(),
                s.getDataQuality(),
                s.getPriceSource(),
                s.getPriceLatencyMs()
        );
    }

    private ViopSnapshotResponse build(
            String contractCode,
            BigDecimal price,
            BigDecimal spot,
            Long oi,
            Long dailyVolume,
            LocalDateTime asOf,
            String source,
            BigDecimal providedBasis,
            BigDecimal providedMargin,
            Integer providedDaysToExpiry,
            String dataQuality,
            String priceSource,
            Long priceLatencyMs
    ) {
        String normalizedCode = viopContractParser.normalizeContractCode(contractCode);
        String expiry = contractRepository.findByContractCode(normalizedCode).map(c -> c.getExpiry()).orElse(null);
        if (expiry == null || expiry.isBlank()) {
            expiry = contractRepository.findByContractCode("F_" + normalizedCode).map(c -> c.getExpiry()).orElse(null);
        }
        String contractMonth = expiryToContractMonth(expiry);
        BigDecimal safeSpot = spot == null || spot.signum() == 0 ? BigDecimal.ONE : spot;
        BigDecimal basis = providedBasis != null ? providedBasis : price.subtract(safeSpot);
        BigDecimal annualized = basis.divide(safeSpot, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("36500"))
                .divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
        String regime = basis.signum() >= 0 && oi > 0 ? "PRICE_UP_OI_UP" : "NEUTRAL";
        BigDecimal marginRequirement = providedMargin != null
                ? providedMargin
                : price.multiply(new BigDecimal("0.12")).setScale(4, RoundingMode.HALF_UP);
        String longShortIndicator = basis.signum() > 0 ? "LONG" : basis.signum() < 0 ? "SHORT" : "NEUTRAL";
        Integer daysToExpiry = providedDaysToExpiry != null ? providedDaysToExpiry : calculateDaysToExpiry(expiry);
        return new ViopSnapshotResponse(
                normalizedCode,
                expiry,
                contractMonth,
                price,
                safeSpot,
                basis,
                annualized,
                marginRequirement,
                longShortIndicator,
                oi,
                dailyVolume,
                regime,
                source,
                asOf,
                daysToExpiry,
                dataQuality == null ? "EXACT" : dataQuality,
                priceSource == null ? source : priceSource,
                priceLatencyMs
        );
    }

    private Integer calculateDaysToExpiry(String expiry) {
        if (expiry == null || expiry.isBlank()) return null;
        try {
            java.time.LocalDate now = java.time.LocalDate.now();
            java.time.LocalDate e = java.time.LocalDate.parse(expiry);
            return (int) java.time.temporal.ChronoUnit.DAYS.between(now, e);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ViopMarketWatchResponse.Leader toLeader(ViopSnapshotResponse row) {
        return new ViopMarketWatchResponse.Leader(
                row.contractCode(),
                row.price(),
                row.annualizedBasisPct(),
                row.openInterest(),
                row.dailyVolume(),
                row.source(),
                row.dataQuality()
        );
    }

    private long nullSafeLong(Long value) {
        return value == null ? 0L : value;
    }

    private List<DerivativeSnapshot> loadSnapshotsByAlias(String contract) {
        String normalized = viopContractParser.normalizeContractCode(contract);
        List<DerivativeSnapshot> rows = snapshotRepository.findByContractCodeOrderByAsOfAsc(normalized);
        if (!rows.isEmpty()) {
            return rows;
        }
        return snapshotRepository.findByContractCodeOrderByAsOfAsc("F_" + normalized);
    }

    private List<OpenInterestSnapshot> loadOiByAlias(String contract) {
        String normalized = viopContractParser.normalizeContractCode(contract);
        List<OpenInterestSnapshot> rows = openInterestSnapshotRepository.findByContractCodeOrderByAsOfAsc(normalized);
        if (!rows.isEmpty()) {
            return rows;
        }
        return openInterestSnapshotRepository.findByContractCodeOrderByAsOfAsc("F_" + normalized);
    }

    private DerivativeSnapshot findLatestByAlias(String contract) {
        String normalized = viopContractParser.normalizeContractCode(contract);
        return snapshotRepository.findTopByContractCodeOrderByAsOfDesc(normalized)
                .or(() -> snapshotRepository.findTopByContractCodeOrderByAsOfDesc("F_" + normalized))
                .orElse(null);
    }

    private OpenInterestSnapshot findLatestOiByAlias(String contract) {
        String normalized = viopContractParser.normalizeContractCode(contract);
        return openInterestSnapshotRepository.findTopByContractCodeOrderByAsOfDesc(normalized)
                .or(() -> openInterestSnapshotRepository.findTopByContractCodeOrderByAsOfDesc("F_" + normalized))
                .orElse(null);
    }

    private String expiryToContractMonth(String expiry) {
        if (expiry == null || expiry.isBlank()) return null;
        try {
            String[] parts = expiry.split("-");
            if (parts.length >= 2) {
                int month = Integer.parseInt(parts[1]);
                String monthName = switch (month) {
                    case 1 -> "Oca";
                    case 2 -> "Şub";
                    case 3 -> "Mar";
                    case 4 -> "Nis";
                    case 5 -> "May";
                    case 6 -> "Haz";
                    case 7 -> "Tem";
                    case 8 -> "Ağu";
                    case 9 -> "Eyl";
                    case 10 -> "Eki";
                    case 11 -> "Kas";
                    case 12 -> "Ara";
                    default -> null;
                };
                if (monthName != null) return monthName + " " + parts[0];
            }
            return expiry;
        } catch (Exception ignored) {
            return expiry;
        }
    }
}
