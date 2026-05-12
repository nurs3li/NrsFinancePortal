package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.ViopContractResponse;
import com.nurseli.marketdata.api.dto.ViopMarketWatchResponse;
import com.nurseli.marketdata.api.dto.ViopSnapshotResponse;
import com.nurseli.marketdata.config.ViopQueryProperties;
import com.nurseli.marketdata.domain.derivatives.DerivativeContract;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViopQueryService {
    /** Gün sınırı (İstanbul takvimi): liste % ve sparkline gün başına tek nokta. */
    private static final ZoneId VIOP_DAY_ZONE = ZoneId.of("Europe/Istanbul");
    private static final int TERMINAL_LIST_CALENDAR_DAYS = 14;
    private static final int SNAPSHOT_LOAD_EXTRA_DAYS = 10;

    private final DerivativeContractRepository contractRepository;
    private final DerivativeSnapshotRepository snapshotRepository;
    private final OpenInterestSnapshotRepository openInterestSnapshotRepository;
    private final ViopContractParser viopContractParser;
    private final ViopQueryProperties queryProperties;

    /**
     * Whitelist'i normalize edilmiş kodlardan oluşan bir Set olarak sunar.
     * Boş ise filtre devre dışıdır.
     */
    private Set<String> allowedNormalizedCodes() {
        List<String> raw = queryProperties.getAllowedContracts();
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>(raw.size() * 2);
        for (String c : raw) {
            if (c == null || c.isBlank()) continue;
            String normalized = viopContractParser.normalizeContractCode(c.trim());
            if (normalized != null && !normalized.isBlank()) {
                out.add(normalized);
                // ek alias: bazı kodlar DB'de "F_" öneksiz tutuluyor olabilir
                if (normalized.startsWith("F_")) {
                    out.add(normalized.substring(2));
                } else {
                    out.add("F_" + normalized);
                }
            }
        }
        return out;
    }

    private boolean isWhitelisted(String rawCode, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) return true;
        if (rawCode == null) return false;
        if (allowed.contains(rawCode)) return true;
        String normalized = viopContractParser.normalizeContractCode(rawCode);
        if (normalized == null) return false;
        return allowed.contains(normalized) || allowed.contains("F_" + normalized);
    }

    public List<ViopContractResponse> contracts() {
        Set<String> allowed = allowedNormalizedCodes();
        return contractRepository.findAll().stream()
                .filter(c -> isWhitelisted(c.getContractCode(), allowed))
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
                        c.getType(),
                        c.getViopListPctChange1d(),
                        c.getViopListPctChange7d(),
                        c.getViopListPctChange30d(),
                        c.getViopListPctChange365d(),
                        c.getViopSeqMovePct(),
                        c.getViopSeqMoveTrend()
                ))
                .toList();
    }

    public List<ViopSnapshotResponse> latest() {
        Set<String> allowed = allowedNormalizedCodes();
        List<DerivativeSnapshot> mergedLatest = snapshotRepository.findLatestSnapshotPerContract().stream()
                .filter(s -> isWhitelisted(s.getContractCode(), allowed))
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
                .toList();
        Map<String, TerminalRollup> rollups = loadTerminalRollups(mergedLatest);
        // N+1 önleyici batch yükler: contract & OI lookup'larını tek seferde Map'e koyup
        // her response satırında yeniden DB'ye sormuyoruz.
        Map<String, DerivativeContract> contractIndex = buildContractIndex();
        Map<String, OpenInterestSnapshot> latestOiIndex = buildLatestOiIndex();
        List<ViopSnapshotResponse> rows = mergedLatest.stream()
                .map(s -> toSnapshotResponse(
                        s,
                        rollups.get(s.getContractCode()),
                        latestOiIndex,
                        contractIndex))
                .toList();
        if (rows.isEmpty()) {
            log.info("[VIOP] latest() returned empty list (no fresh snapshots)");
        }
        return rows;
    }

    public List<ViopSnapshotResponse> history(String contract, int days) {
        Set<String> allowed = allowedNormalizedCodes();
        if (!isWhitelisted(contract, allowed)) {
            return List.of();
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return loadSnapshotsByAlias(contract).stream()
                .filter(s -> !s.getAsOf().isBefore(cutoff))
                .map(this::toSnapshotResponse)
                .toList();
    }

    public List<ViopSnapshotResponse> oiHistory(String contract, int days) {
        Set<String> allowed = allowedNormalizedCodes();
        if (!isWhitelisted(contract, allowed)) {
            return List.of();
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        // Latest snapshot'ı döngü dışına aldık (önceden her OI satırı için tekrar DB'ye gidiyordu).
        DerivativeSnapshot latestSnapshot = findLatestByAlias(contract);
        BigDecimal spot = latestSnapshot != null ? latestSnapshot.getTheoreticalSpot() : BigDecimal.ONE;
        BigDecimal price = latestSnapshot != null ? latestSnapshot.getPrice() : BigDecimal.ONE;
        String source = latestSnapshot != null ? latestSnapshot.getSource() : "VIOP";
        BigDecimal basis = latestSnapshot != null ? latestSnapshot.getBasis() : null;
        BigDecimal margin = latestSnapshot != null ? latestSnapshot.getMaintenanceMargin() : null;
        Integer daysToExpiry = latestSnapshot != null ? latestSnapshot.getDaysToExpiry() : null;
        String dataQuality = latestSnapshot != null ? latestSnapshot.getDataQuality() : null;
        String priceSource = latestSnapshot != null ? latestSnapshot.getPriceSource() : null;
        Long priceLatencyMs = latestSnapshot != null ? latestSnapshot.getPriceLatencyMs() : null;
        String normalizedContract = viopContractParser.normalizeContractCode(contract);
        Map<String, DerivativeContract> contractIndex = buildContractIndex();

        return loadOiByAlias(contract).stream()
                .filter(oi -> !oi.getAsOf().isBefore(cutoff))
                .map(oi -> build(
                        normalizedContract,
                        price,
                        spot,
                        oi.getOpenInterest(),
                        oi.getDailyVolume(),
                        oi.getAsOf(),
                        source,
                        basis,
                        margin,
                        daysToExpiry,
                        dataQuality,
                        priceSource,
                        priceLatencyMs,
                        null,
                        null,
                        contractIndex
                ))
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

    private record TerminalRollup(List<BigDecimal> sparklineDailyCloses, BigDecimal listPctChange14d) {}

    private static final TerminalRollup EMPTY_TERMINAL_ROLLUP = new TerminalRollup(List.of(), null);

    private ViopSnapshotResponse toSnapshotResponse(DerivativeSnapshot s) {
        return toSnapshotResponse(s, EMPTY_TERMINAL_ROLLUP, null, null);
    }

    private ViopSnapshotResponse toSnapshotResponse(
            DerivativeSnapshot s,
            TerminalRollup rollup,
            Map<String, OpenInterestSnapshot> latestOiIndex,
            Map<String, DerivativeContract> contractIndex
    ) {
        TerminalRollup r = rollup == null ? EMPTY_TERMINAL_ROLLUP : rollup;
        OpenInterestSnapshot latestOi = lookupLatestOi(s.getContractCode(), latestOiIndex);
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
                s.getPriceLatencyMs(),
                r.listPctChange14d(),
                r.sparklineDailyCloses(),
                contractIndex
        );
    }

    /** Tüm DerivativeContract satırlarını tek query ile Map<normalizedCode, contract> haline getirir. */
    private Map<String, DerivativeContract> buildContractIndex() {
        Map<String, DerivativeContract> idx = new HashMap<>();
        for (DerivativeContract c : contractRepository.findAll()) {
            String code = c.getContractCode();
            if (code == null) continue;
            idx.putIfAbsent(code, c);
            String normalized = viopContractParser.normalizeContractCode(code);
            if (!Objects.equals(code, normalized)) {
                idx.putIfAbsent(normalized, c);
            }
            if (normalized != null && !normalized.isBlank() && !normalized.startsWith("F_")) {
                idx.putIfAbsent("F_" + normalized, c);
            }
        }
        return idx;
    }

    /** Tüm contract'lar için en son OI snapshot'ı tek query ile Map'e koyar. */
    private Map<String, OpenInterestSnapshot> buildLatestOiIndex() {
        Map<String, OpenInterestSnapshot> idx = new HashMap<>();
        for (OpenInterestSnapshot oi : openInterestSnapshotRepository.findLatestPerContract()) {
            String code = oi.getContractCode();
            if (code == null) continue;
            idx.put(code, oi);
            String normalized = viopContractParser.normalizeContractCode(code);
            if (normalized != null && !Objects.equals(normalized, code)) {
                idx.putIfAbsent(normalized, oi);
            }
        }
        return idx;
    }

    private OpenInterestSnapshot lookupLatestOi(String rawCode, Map<String, OpenInterestSnapshot> idx) {
        if (idx == null) {
            return findLatestOiByAlias(rawCode);
        }
        if (rawCode == null) return null;
        OpenInterestSnapshot direct = idx.get(rawCode);
        if (direct != null) return direct;
        String normalized = viopContractParser.normalizeContractCode(rawCode);
        OpenInterestSnapshot byNormalized = idx.get(normalized);
        if (byNormalized != null) return byNormalized;
        return idx.get("F_" + normalized);
    }

    private Optional<DerivativeContract> lookupContract(String normalizedCode, Map<String, DerivativeContract> idx) {
        if (idx != null) {
            DerivativeContract c = idx.get(normalizedCode);
            if (c == null) c = idx.get("F_" + normalizedCode);
            if (c != null) return Optional.of(c);
        }
        return contractRepository.findByContractCode(normalizedCode)
                .or(() -> contractRepository.findByContractCode("F_" + normalizedCode));
    }

    private Map<String, TerminalRollup> loadTerminalRollups(List<DerivativeSnapshot> mergedLatest) {
        if (mergedLatest == null || mergedLatest.isEmpty()) {
            return Map.of();
        }
        Set<String> codes = mergedLatest.stream()
                .map(DerivativeSnapshot::getContractCode)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        LocalDateTime minLatestAsOf = mergedLatest.stream()
                .map(DerivativeSnapshot::getAsOf)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(LocalDateTime.now());
        LocalDateTime since = minLatestAsOf.minusDays(TERMINAL_LIST_CALENDAR_DAYS + SNAPSHOT_LOAD_EXTRA_DAYS);
        List<DerivativeSnapshot> rows = snapshotRepository.findByContractCodeInAndAsOfSince(codes, since);
        Map<String, List<DerivativeSnapshot>> byCode = rows.stream()
                .collect(Collectors.groupingBy(DerivativeSnapshot::getContractCode, LinkedHashMap::new, Collectors.toList()));
        for (List<DerivativeSnapshot> list : byCode.values()) {
            list.sort(Comparator.comparing(DerivativeSnapshot::getAsOf, Comparator.nullsFirst(Comparator.naturalOrder())));
        }
        Map<String, TerminalRollup> out = new HashMap<>();
        for (DerivativeSnapshot latestRow : mergedLatest) {
            String code = latestRow.getContractCode();
            if (code == null || code.isBlank()) {
                continue;
            }
            LocalDateTime tEnd = latestRow.getAsOf() != null ? latestRow.getAsOf() : LocalDateTime.now();
            List<DerivativeSnapshot> contractRows = byCode.getOrDefault(code, List.of());
            out.put(code, buildTerminalRollup(contractRows, tEnd));
        }
        return out;
    }

    private LocalDateTime windowStartInclusive(LocalDateTime tEnd, int calendarDaysInclusive) {
        if (calendarDaysInclusive < 1) {
            throw new IllegalArgumentException("calendarDaysInclusive");
        }
        LocalDate endDay = tEnd.atZone(VIOP_DAY_ZONE).toLocalDate();
        LocalDate startDay = endDay.minusDays(calendarDaysInclusive - 1);
        return startDay.atStartOfDay(VIOP_DAY_ZONE).toLocalDateTime();
    }

    private TerminalRollup buildTerminalRollup(List<DerivativeSnapshot> ascRows, LocalDateTime tEnd) {
        LocalDateTime end = tEnd;
        if (end == null) {
            end = ascRows.isEmpty() ? LocalDateTime.now() : ascRows.get(ascRows.size() - 1).getAsOf();
            if (end == null) {
                end = LocalDateTime.now();
            }
        }
        final LocalDateTime tEndFinal = end;
        LocalDateTime tStart = windowStartInclusive(tEndFinal, TERMINAL_LIST_CALENDAR_DAYS);
        List<DerivativeSnapshot> inWin = ascRows.stream()
                .filter(x -> x.getAsOf() != null
                        && !x.getAsOf().isBefore(tStart)
                        && !x.getAsOf().isAfter(tEndFinal))
                .filter(x -> x.getPrice() != null && x.getPrice().signum() > 0)
                .toList();
        TreeMap<LocalDate, DerivativeSnapshot> dayLast = new TreeMap<>();
        for (DerivativeSnapshot x : inWin) {
            LocalDate d = x.getAsOf().atZone(VIOP_DAY_ZONE).toLocalDate();
            dayLast.merge(d, x, (a, b) -> a.getAsOf().isBefore(b.getAsOf()) ? b : a);
        }
        List<BigDecimal> closes = dayLast.values().stream()
                .sorted(Comparator.comparing(DerivativeSnapshot::getAsOf))
                .map(DerivativeSnapshot::getPrice)
                .toList();
        BigDecimal pct = null;
        if (closes.size() >= 2) {
            BigDecimal first = closes.get(0);
            BigDecimal last = closes.get(closes.size() - 1);
            if (first.signum() > 0 && last.signum() > 0) {
                pct = last.subtract(first)
                        .divide(first, 8, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(4, RoundingMode.HALF_UP);
            }
        }
        return new TerminalRollup(List.copyOf(closes), pct);
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
            Long priceLatencyMs,
            BigDecimal listPctChange14dTerminal,
            List<BigDecimal> sparklineCloses
    ) {
        return build(contractCode, price, spot, oi, dailyVolume, asOf, source,
                providedBasis, providedMargin, providedDaysToExpiry,
                dataQuality, priceSource, priceLatencyMs,
                listPctChange14dTerminal, sparklineCloses, null);
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
            Long priceLatencyMs,
            BigDecimal listPctChange14dTerminal,
            List<BigDecimal> sparklineCloses,
            Map<String, DerivativeContract> contractIndex
    ) {
        String normalizedCode = viopContractParser.normalizeContractCode(contractCode);
        Optional<DerivativeContract> contractOpt = lookupContract(normalizedCode, contractIndex);
        String expiry = contractOpt.map(DerivativeContract::getExpiry).orElse(null);
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
                priceLatencyMs,
                contractOpt.map(DerivativeContract::getViopListPctChange1d).orElse(null),
                contractOpt.map(DerivativeContract::getViopListPctChange7d).orElse(null),
                contractOpt.map(DerivativeContract::getViopListPctChange30d).orElse(null),
                contractOpt.map(DerivativeContract::getViopListPctChange365d).orElse(null),
                listPctChange14dTerminal,
                contractOpt.map(DerivativeContract::getViopSeqMovePct).orElse(null),
                contractOpt.map(DerivativeContract::getViopSeqMoveTrend).orElse(null),
                sparklineCloses
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
