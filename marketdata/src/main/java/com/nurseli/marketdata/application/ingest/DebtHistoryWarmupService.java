package com.nurseli.marketdata.application.ingest;

import com.nurseli.marketdata.api.dto.DebtHistoryCoverageResponse;
import com.nurseli.marketdata.api.dto.DebtHistoryWarmupResponse;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import com.nurseli.marketdata.infrastructure.persistence.DebtSnapshotRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtHistoryWarmupService {

    private static final ZoneId MARKET_WALL_CLOCK_ZONE = ZoneId.of("Europe/Istanbul");
    private static final int MAX_RANGE_CHUNK_DAYS = 180;
    private static final long DELAY_BETWEEN_CHUNKS_MS = 350L;

    private final Set<String> inFlightWarmups = ConcurrentHashMap.newKeySet();
    private final ExecutorService warmupExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "debt-history-warmup");
        thread.setDaemon(true);
        return thread;
    });

    private final EvdsProperties evdsProperties;
    private final DebtSnapshotRepository debtSnapshotRepository;
    private final DebtIngestService debtIngestService;

    public DebtHistoryCoverageResponse getCoverage(String rawIsin, LocalDate requestedFrom, LocalDate requestedTo) {
        String isin = normalizeIsin(rawIsin);
        LocalDate from = requestedFrom;
        LocalDate to = effectiveTo(requestedTo);
        if (isin == null || from == null || to == null || to.isBefore(from)) {
            return new DebtHistoryCoverageResponse(rawIsin, requestedFrom, requestedTo, null, null, 0, 0, false);
        }
        Optional<DebtSnapshot> oldest = debtSnapshotRepository.findTopByIsinOrderByAsOfAsc(isin);
        Optional<DebtSnapshot> newest = debtSnapshotRepository.findTopByIsinOrderByAsOfDesc(isin);
        LocalDateTime start = from.atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
        long availableDays = debtSnapshotRepository.countDistinctObservationDays(isin, start, endExclusive);
        long expectedDays = ChronoUnit.DAYS.between(from, to) + 1L;
        LocalDate oldestDay = oldest.map(DebtSnapshot::getAsOf).map(LocalDateTime::toLocalDate).orElse(null);
        LocalDate newestDay = newest.map(DebtSnapshot::getAsOf).map(LocalDateTime::toLocalDate).orElse(null);
        boolean ready = oldestDay != null
                && newestDay != null
                && !oldestDay.isAfter(from)
                && !newestDay.isBefore(to)
                && availableDays >= expectedDays;
        return new DebtHistoryCoverageResponse(isin, from, to, oldestDay, newestDay, availableDays, expectedDays, ready);
    }

    public DebtHistoryWarmupResponse requestWarmup(
            List<String> rawIsins,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            String reason) {
        LocalDate from = requestedFrom;
        LocalDate to = effectiveTo(requestedTo);
        if (!warmupEnabled()) {
            return new DebtHistoryWarmupResponse(
                    "DISABLED",
                    0,
                    0,
                    requestedFrom,
                    requestedTo,
                    "Debt EVDS history disabled.");
        }
        if (from == null || to == null || to.isBefore(from)) {
            return new DebtHistoryWarmupResponse(
                    "INVALID_REQUEST",
                    0,
                    0,
                    requestedFrom,
                    requestedTo,
                    "from/to gecersiz");
        }
        List<String> isins = normalizeIsins(rawIsins);
        int queued = 0;
        int running = 0;
        int ready = 0;
        for (String isin : isins) {
            DebtHistoryCoverageResponse coverage = getCoverage(isin, from, to);
            if (coverage.ready()) {
                ready++;
                continue;
            }
            String warmupKey = warmupKey(isin, from, to);
            if (!inFlightWarmups.add(warmupKey)) {
                running++;
                continue;
            }
            queued++;
            warmupExecutor.execute(() -> {
                try {
                    warmupSync(isin, from, to, reason);
                } finally {
                    inFlightWarmups.remove(warmupKey);
                }
            });
        }
        String status = queued > 0 ? "QUEUED" : running > 0 ? "IN_FLIGHT" : ready == isins.size() ? "READY" : "NOOP";
        String message = queued > 0
                ? "Debt history warmup kuyruga alindi."
                : running > 0
                ? "Debt history warmup zaten calisiyor."
                : ready == isins.size()
                ? "Debt history zaten hazir."
                : "Warmup baslatilamadi.";
        return new DebtHistoryWarmupResponse(status, isins.size(), queued, from, to, message);
    }

    public void requestWarmupIfMissing(String rawIsin, LocalDate requestedFrom, LocalDate requestedTo, String reason) {
        if (!warmupEnabled()) {
            return;
        }
        DebtHistoryCoverageResponse coverage = getCoverage(rawIsin, requestedFrom, requestedTo);
        if (coverage.ready() || coverage.isin() == null || coverage.requestedFrom() == null || coverage.requestedTo() == null) {
            return;
        }
        String warmupKey = warmupKey(coverage.isin(), coverage.requestedFrom(), coverage.requestedTo());
        if (!inFlightWarmups.add(warmupKey)) {
            return;
        }
        log.info(
                "[DEBT_HISTORY_WARMUP] queued isin={} from={} to={} availableDays={} expectedDays={} reason={}",
                coverage.isin(),
                coverage.requestedFrom(),
                coverage.requestedTo(),
                coverage.availableDays(),
                coverage.expectedDays(),
                reason);
        warmupExecutor.execute(() -> {
            try {
                warmupSync(coverage.isin(), coverage.requestedFrom(), coverage.requestedTo(), reason);
            } finally {
                inFlightWarmups.remove(warmupKey);
            }
        });
    }

    public void warmConfiguredIsinsSync(int periodDays, String reason) {
        if (!warmupEnabled()) {
            return;
        }
        int days = Math.max(30, periodDays);
        LocalDate to = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        LocalDate from = to.minusDays(days - 1L);
        for (String isin : normalizeIsins(List.of())) {
            warmupSync(isin, from, to, reason);
        }
    }

    public void warmupSync(String rawIsin, LocalDate requestedFrom, LocalDate requestedTo, String reason) {
        String isin = normalizeIsin(rawIsin);
        LocalDate from = requestedFrom;
        LocalDate to = effectiveTo(requestedTo);
        if (!warmupEnabled() || isin == null || from == null || to == null || to.isBefore(from)) {
            return;
        }
        DebtHistoryCoverageResponse coverage = getCoverage(isin, from, to);
        if (coverage.ready()) {
            log.debug("[DEBT_HISTORY_WARMUP] already_ready isin={} from={} to={}", isin, from, to);
            return;
        }
        LocalDate chunkStart = from;
        int inserted = 0;
        while (!chunkStart.isAfter(to)) {
            LocalDate chunkEnd = min(chunkStart.plusDays(MAX_RANGE_CHUNK_DAYS - 1L), to);
            int chunkInserted = debtIngestService.ingestHistoryRange(isin, chunkStart, chunkEnd, reason);
            inserted += chunkInserted;
            log.info(
                    "[DEBT_HISTORY_WARMUP] chunk_done isin={} from={} to={} inserted={} reason={}",
                    isin,
                    chunkStart,
                    chunkEnd,
                    chunkInserted,
                    reason);
            chunkStart = chunkEnd.plusDays(1);
            if (!chunkStart.isAfter(to)) {
                sleepQuietly(DELAY_BETWEEN_CHUNKS_MS);
            }
        }
        log.info("[DEBT_HISTORY_WARMUP] completed isin={} from={} to={} inserted={} reason={}", isin, from, to, inserted, reason);
    }

    private boolean warmupEnabled() {
        return evdsProperties.isEnabled() && evdsProperties.getDebt() != null && evdsProperties.getDebt().isEnabled();
    }

    private List<String> normalizeIsins(List<String> rawIsins) {
        List<String> out = new ArrayList<>();
        if (rawIsins == null || rawIsins.isEmpty()) {
            if (evdsProperties.getDebt() == null || evdsProperties.getDebt().getInstruments() == null) {
                return List.of();
            }
            evdsProperties.getDebt().getInstruments().stream()
                    .map(EvdsProperties.Instrument::getIsin)
                    .map(this::normalizeIsin)
                    .filter(isin -> isin != null && !isin.isBlank())
                    .sorted(Comparator.naturalOrder())
                    .forEach(out::add);
            return out;
        }
        for (String rawIsin : rawIsins) {
            String normalized = normalizeIsin(rawIsin);
            if (normalized != null && !out.contains(normalized)) {
                out.add(normalized);
            }
        }
        return out.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private String normalizeIsin(String rawIsin) {
        if (rawIsin == null || rawIsin.isBlank()) {
            return null;
        }
        String normalized = rawIsin.trim().toUpperCase();
        if (evdsProperties.getDebt() == null || evdsProperties.getDebt().getInstruments() == null) {
            return normalized;
        }
        return evdsProperties.getDebt().getInstruments().stream()
                .map(EvdsProperties.Instrument::getIsin)
                .map(isin -> isin == null ? null : isin.trim().toUpperCase())
                .filter(normalized::equals)
                .findFirst()
                .orElse(normalized);
    }

    private static LocalDate effectiveTo(LocalDate requestedTo) {
        LocalDate today = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        if (requestedTo == null || requestedTo.isAfter(today)) {
            return today;
        }
        return requestedTo;
    }

    private static LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private static String warmupKey(String isin, LocalDate from, LocalDate to) {
        return isin + "|" + from + "|" + to;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void shutdownExecutor() {
        warmupExecutor.shutdownNow();
    }
}
