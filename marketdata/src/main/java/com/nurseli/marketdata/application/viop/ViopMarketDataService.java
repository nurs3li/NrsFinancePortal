package com.nurseli.marketdata.application.viop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nurseli.marketdata.api.dto.ViopContractResponse;
import com.nurseli.marketdata.api.dto.ViopHistoryResponse;
import com.nurseli.marketdata.api.dto.ViopMarketContractDto;
import com.nurseli.marketdata.api.dto.ViopMarketSnapshotDto;
import com.nurseli.marketdata.api.dto.ViopPriceAtResponse;
import com.nurseli.marketdata.api.dto.ViopPricePoint;
import com.nurseli.marketdata.config.MarketViopProperties;
import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import com.nurseli.marketdata.domain.viop.ViopPriceHistoryEntity;
import com.nurseli.marketdata.domain.viop.ViopSnapshotEntity;
import com.nurseli.marketdata.infrastructure.persistence.DerivativeSnapshotRepository;
import com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopClient;
import com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopHistoricalParser;
import com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopSnapshotParser;
import com.nurseli.marketdata.infrastructure.persistence.ViopPriceHistoryRepository;
import com.nurseli.marketdata.infrastructure.persistence.ViopSnapshotRepository;
import com.nurseli.marketdata.domain.viop.ViopDataQuality;
import com.nurseli.marketdata.domain.viop.ViopPriceMatchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViopMarketDataService {

    private static final String SOURCE_ISYATIRIM = "IS_YATIRIM";
    private static final String SOURCE_LABEL_PROVIDER = "İş Yatırım";
    private static final String SOURCE_LABEL_DB = "DB";
    private static final String SOURCE_LABEL_DERIVATIVE_SNAPSHOT = "VIOP_SNAPSHOT_SERIES";

    private static final ObjectMapper REDIS_JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final MarketViopProperties viopProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final IsYatirimViopClient isYatirimViopClient;
    private final IsYatirimViopHistoricalParser historicalParser;
    private final IsYatirimViopSnapshotParser snapshotParser;
    private final ViopPriceHistoryRepository priceHistoryRepository;
    private final ViopSnapshotRepository snapshotRepository;
    private final DerivativeSnapshotRepository derivativeSnapshotRepository;
    private final ViopQueryService viopQueryService;

    public List<ViopMarketContractDto> listContracts(boolean includeExpired) {
        ZoneId zone = ZoneId.of(viopProperties.getTimezone());
        YearMonth nowYm = YearMonth.now(zone);
        if (!viopProperties.isEnabled()) {
            return viopQueryService.contracts().stream().map(this::fromLegacy).toList();
        }
        return enabledEntries()
                .map(e -> toContractDto(e, nowYm, includeExpired))
                .filter(Objects::nonNull)
                .toList();
    }

    public ViopMarketContractDto getContract(String contractCode) {
        if (!viopProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "VIOP integration disabled");
        }
        MarketViopProperties.IndexEntry entry = resolveEntry(contractCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or disabled VIOP contract"));
        ZoneId zone = ZoneId.of(viopProperties.getTimezone());
        YearMonth nowYm = YearMonth.now(zone);
        ViopMarketContractDto dto = toContractDto(entry, nowYm, true);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or disabled VIOP contract");
        }
        return dto;
    }

    public ViopMarketSnapshotDto getSnapshot(String contractCode) {
        ensureEnabled();
        MarketViopProperties.IndexEntry entry = resolveEntry(contractCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or disabled VIOP contract"));
        String canonical = canonical(entry);
        String cacheKey = snapshotRedisKey(canonical);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return REDIS_JSON.readValue(cached, ViopMarketSnapshotDto.class);
            }
        } catch (Exception e) {
            log.warn("VIOP snapshot cache read failed {}: {}", cacheKey, e.getMessage());
        }

        Optional<ViopSnapshotEntity> latest = snapshotRepository.findTopByContractCodeOrderByUpdateDateDesc(canonical);
        if (latest.isPresent()) {
            ViopMarketSnapshotDto dto = fromEntity(latest.get(), entry, SOURCE_LABEL_DB, ViopDataQuality.OK.name());
            writeSnapshotRedis(cacheKey, dto);
            return dto;
        }
        return fetchSnapshotFromProvider(entry, canonical, cacheKey);
    }

    @Transactional
    public ViopHistoryResponse getHistory(String contractCode, LocalDateTime from, LocalDateTime to, int periodMinutes) {
        ensureEnabled();
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`");
        }
        MarketViopProperties.IndexEntry entry = resolveEntry(contractCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or disabled VIOP contract"));
        String canonical = canonical(entry);
        int period = periodMinutes > 0 ? periodMinutes : viopProperties.getDefaultPeriodMinutes();
        ZoneId zone = ZoneId.of(viopProperties.getTimezone());
        String cacheKey = historyRedisKey(canonical, from, to, period);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                ViopHistoryResponse dto = REDIS_JSON.readValue(cached, ViopHistoryResponse.class);
                if (!viopHistoryCachedResponseTailStale(dto, to, period, zone)) {
                    return dto;
                }
                stringRedisTemplate.delete(cacheKey);
            }
        } catch (Exception e) {
            log.warn("VIOP history cache read failed {}: {}", cacheKey, e.getMessage());
        }

        List<ViopPriceHistoryEntity> rows =
                priceHistoryRepository.findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(canonical, from, to);
        if (rows.isEmpty()) {
            long t0 = System.currentTimeMillis();
            try {
                IsYatirimViopHistoricalParser.ParsedHistorical parsed =
                        upsertHistoricalWindowParsed(canonical, entry, from, to, period, zone, t0, "full_window");
                rows = priceHistoryRepository.findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(canonical, from, to);
                String quality =
                        rows.isEmpty() && parsed.rows().isEmpty()
                                ? ViopDataQuality.EMPTY_RESPONSE.name()
                                : ViopDataQuality.OK.name();
                ViopHistoryResponse dto =
                        buildHistory(entry, rows, period, from, to, parsed.providerTimestamp(), quality, SOURCE_LABEL_PROVIDER);
                writeHistoryRedis(cacheKey, dto);
                return dto;
            } catch (Exception e) {
                log.warn(
                        "VIOP history provider failed contractCode={} from={} to={} period={} error={}",
                        canonical,
                        from,
                        to,
                        period,
                        e.getMessage());
                rows = priceHistoryRepository.findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(canonical, from, to);
                if (!rows.isEmpty()) {
                    ViopHistoryResponse dto =
                            buildHistory(entry, rows, period, from, to, null, ViopDataQuality.STALE_CACHE.name(), SOURCE_LABEL_DB);
                    writeHistoryRedis(cacheKey, dto);
                    return dto;
                }
                return buildHistory(
                        entry, List.of(), period, from, to, null, ViopDataQuality.PROVIDER_ERROR.name(), SOURCE_LABEL_DB);
            }
        }

        LocalDateTime latestPt = rows.get(rows.size() - 1).getPriceTime();
        if (historyTailNeedsRefresh(latestPt, to, period)) {
            LocalDateTime fetchFrom = latestPt.plusNanos(1L);
            if (fetchFrom.isBefore(to)) {
                long t0 = System.currentTimeMillis();
                try {
                    upsertHistoricalWindowParsed(canonical, entry, fetchFrom, to, period, zone, t0, "tail_merge");
                } catch (Exception e) {
                    log.warn(
                            "VIOP history tail merge failed contractCode={} fetchFrom={} to={} period={} error={}",
                            canonical,
                            fetchFrom,
                            to,
                            period,
                            e.getMessage());
                }
                rows = priceHistoryRepository.findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(canonical, from, to);
            }
        }
        ViopHistoryResponse dto =
                buildHistory(entry, rows, period, from, to, null, ViopDataQuality.OK.name(), SOURCE_LABEL_DB);
        writeHistoryRedis(cacheKey, dto);
        return dto;
    }

    /**
     * DB'de son mum {@code to} için yeterince güncel değilse (en az bir {@code period} geride) İş Yatırım'dan kuyruk
     * doldurulur; aksi halde eski grafik "200 OK" ile takılı kalırdı.
     */
    private static boolean historyTailNeedsRefresh(LocalDateTime latestPriceTime, LocalDateTime requestTo, int periodMinutes) {
        int pm = Math.max(1, periodMinutes);
        return latestPriceTime.isBefore(requestTo.minusMinutes(pm));
    }

    private static boolean viopHistoryCachedResponseTailStale(
            ViopHistoryResponse cached, LocalDateTime requestTo, int periodMinutes, ZoneId zone) {
        if (cached == null || cached.points() == null || cached.points().isEmpty()) {
            return true;
        }
        ViopPricePoint last = cached.points().get(cached.points().size() - 1);
        if (last.time() == null) {
            return true;
        }
        LocalDateTime latestLocal = last.time().atZone(zone).toLocalDateTime();
        return historyTailNeedsRefresh(latestLocal, requestTo, periodMinutes);
    }

    private IsYatirimViopHistoricalParser.ParsedHistorical upsertHistoricalWindowParsed(
            String canonical,
            MarketViopProperties.IndexEntry entry,
            LocalDateTime from,
            LocalDateTime to,
            int period,
            ZoneId zone,
            long t0,
            String mode)
            throws Exception {
        String raw = isYatirimViopClient.fetchHistorical(canonical, from, to, period);
        IsYatirimViopHistoricalParser.ParsedHistorical parsed = historicalParser.parse(raw);
        OffsetDateTime providerTs = parsed.providerTimestamp();
        LocalDateTime providerLocal =
                providerTs == null ? null : providerTs.atZoneSameInstant(zone).toLocalDateTime();
        int inserted = 0;
        int skipped = 0;
        for (IsYatirimViopHistoricalParser.ChartRow row : parsed.rows()) {
            LocalDateTime pt = Instant.ofEpochMilli(row.millis()).atZone(zone).toLocalDateTime();
            int n = priceHistoryRepository.insertIgnore(
                    canonical,
                    entry.getUnderlying(),
                    entry.getAssetClass(),
                    entry.getSegment(),
                    pt,
                    row.price(),
                    period,
                    SOURCE_ISYATIRIM,
                    providerLocal);
            if (n > 0) {
                inserted++;
            } else {
                skipped++;
            }
        }
        log.info(
                "VIOP_HISTORY_UPSERT_SUCCESS contractCode={} mode={} from={} to={} period={} durationMs={} insertedCount={} skippedCount={} dataQuality={}",
                canonical,
                mode,
                from,
                to,
                period,
                System.currentTimeMillis() - t0,
                inserted,
                skipped,
                parsed.rows().isEmpty() ? ViopDataQuality.EMPTY_RESPONSE : ViopDataQuality.OK);
        return parsed;
    }

    public ViopPriceAtResponse getPriceAt(String contractCode, LocalDate requestedDate) {
        ensureEnabled();
        if (requestedDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        MarketViopProperties.IndexEntry entry = resolveEntry(contractCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or disabled VIOP contract"));
        String canonical = canonical(entry);
        ZoneId zone = ZoneId.of(viopProperties.getTimezone());

        ViopPriceAtResponse hit = resolvePriceAtFromDb(canonical, requestedDate, zone);
        if (!ViopPriceMatchType.NOT_FOUND.name().equals(hit.matchType())) {
            return hit;
        }

        backfillHistoryForPriceAt(canonical, entry, requestedDate, zone);
        hit = resolvePriceAtFromDb(canonical, requestedDate, zone);
        if (!ViopPriceMatchType.NOT_FOUND.name().equals(hit.matchType())) {
            return hit;
        }

        Optional<ViopPriceAtResponse> fromSnapshots =
                resolvePriceAtFromDerivativeSnapshots(canonical, requestedDate, zone);
        if (fromSnapshots.isPresent()) {
            return fromSnapshots.get();
        }

        log.info("VIOP_PRICE_AT_NOT_FOUND contractCode={} requestedDate={}", canonical, requestedDate);
        return hit;
    }

    private ViopPriceAtResponse resolvePriceAtFromDb(String canonical, LocalDate requestedDate, ZoneId zone) {
        LocalDateTime requestedEcho = requestedDate.atStartOfDay(zone).toLocalDateTime();

        for (String code : contractCodeAliases(canonical)) {
            Optional<ViopPriceHistoryEntity> onDay = findLastBarOnCalendarDay(code, requestedDate, zone);
            if (onDay.isPresent()) {
                ViopPriceHistoryEntity pick = onDay.get();
                log.info(
                        "VIOP_PRICE_AT_MATCH contractCode={} requestedDate={} matchType={} priceTime={} dbCode={}",
                        canonical,
                        requestedDate,
                        ViopPriceMatchType.EXACT,
                        pick.getPriceTime(),
                        code);
                return new ViopPriceAtResponse(
                        canonical,
                        requestedEcho,
                        pick.getPriceTime(),
                        pick.getPrice(),
                        ViopPriceMatchType.EXACT.name(),
                        SOURCE_LABEL_DB,
                        ViopDataQuality.OK.name());
            }

            LocalDateTime dayStart = requestedDate.atStartOfDay(zone).toLocalDateTime();
            Optional<ViopPriceHistoryEntity> prev =
                    priceHistoryRepository.findTopByContractCodeAndPriceTimeLessThanOrderByPriceTimeDesc(
                            code, dayStart);
            if (prev.isPresent() && prev.get().getPrice() != null && prev.get().getPrice().signum() > 0) {
                ViopPriceHistoryEntity p = prev.get();
                log.info(
                        "VIOP_PRICE_AT_MATCH contractCode={} requestedDate={} matchType={} priceTime={} dbCode={}",
                        canonical,
                        requestedDate,
                        ViopPriceMatchType.PREVIOUS_AVAILABLE,
                        p.getPriceTime(),
                        code);
                return new ViopPriceAtResponse(
                        canonical,
                        requestedEcho,
                        p.getPriceTime(),
                        p.getPrice(),
                        ViopPriceMatchType.PREVIOUS_AVAILABLE.name(),
                        SOURCE_LABEL_DB,
                        ViopDataQuality.OK.name());
            }
        }

        return new ViopPriceAtResponse(
                canonical,
                requestedEcho,
                null,
                null,
                ViopPriceMatchType.NOT_FOUND.name(),
                SOURCE_LABEL_DB,
                ViopDataQuality.OK.name());
    }

    /**
     * Seçilen takvim gününde (İstanbul) kayıtlı herhangi bir saatteki son bar — örn. 10:00 veya 17:00.
     */
    private Optional<ViopPriceHistoryEntity> findLastBarOnCalendarDay(String code, LocalDate requestedDate, ZoneId zone) {
        LocalDateTime dayStart = requestedDate.atStartOfDay(zone).toLocalDateTime();
        LocalDateTime dayEndExclusive = requestedDate.plusDays(1).atStartOfDay(zone).toLocalDateTime();

        List<ViopPriceHistoryEntity> inDay =
                priceHistoryRepository.findByContractCodeAndPriceTimeGreaterThanEqualAndPriceTimeLessThanOrderByPriceTimeAsc(
                        code, dayStart, dayEndExclusive);
        Optional<ViopPriceHistoryEntity> direct = pickLastValid(inDay);
        if (direct.isPresent()) {
            return direct;
        }

        LocalDateTime slackFrom = requestedDate.minusDays(1).atStartOfDay(zone).toLocalDateTime();
        LocalDateTime slackTo = requestedDate.plusDays(2).atStartOfDay(zone).toLocalDateTime();
        List<ViopPriceHistoryEntity> slack =
                priceHistoryRepository.findByContractCodeAndPriceTimeGreaterThanEqualAndPriceTimeLessThanOrderByPriceTimeAsc(
                        code, slackFrom, slackTo);
        return slack.stream()
                .filter(e -> e.getPriceTime() != null && e.getPrice() != null && e.getPrice().signum() > 0)
                .filter(e -> e.getPriceTime().atZone(zone).toLocalDate().equals(requestedDate))
                .max(Comparator.comparing(ViopPriceHistoryEntity::getPriceTime));
    }

    private static Optional<ViopPriceHistoryEntity> pickLastValid(List<ViopPriceHistoryEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        for (int i = rows.size() - 1; i >= 0; i--) {
            ViopPriceHistoryEntity e = rows.get(i);
            if (e.getPriceTime() != null && e.getPrice() != null && e.getPrice().signum() > 0) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /**
     * Terminal listesi / latest akışının kullandığı {@code derivative_snapshot} serisinden günlük kapanış.
     * {@code mds_viop_price_history} henüz dolu değilken giriş fiyatı çözümü için yedek.
     */
    private Optional<ViopPriceAtResponse> resolvePriceAtFromDerivativeSnapshots(
            String canonical, LocalDate requestedDate, ZoneId zone) {
        Set<String> codes = contractCodeAliases(canonical);
        LocalDateTime since = requestedDate.minusDays(120).atStartOfDay(zone).toLocalDateTime();
        List<DerivativeSnapshot> rows = derivativeSnapshotRepository.findByContractCodeInAndAsOfSince(codes, since);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        TreeMap<LocalDate, DerivativeSnapshot> dayLast = new TreeMap<>();
        for (DerivativeSnapshot row : rows) {
            if (row.getAsOf() == null || row.getPrice() == null || row.getPrice().signum() <= 0) {
                continue;
            }
            LocalDate d = row.getAsOf().atZone(zone).toLocalDate();
            dayLast.merge(d, row, (a, b) -> a.getAsOf().isBefore(b.getAsOf()) ? b : a);
        }
        if (dayLast.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime requestedEcho = requestedDate.atStartOfDay(zone).toLocalDateTime();
        DerivativeSnapshot exact = dayLast.get(requestedDate);
        if (exact != null) {
            log.info(
                    "VIOP_PRICE_AT_SNAPSHOT_FALLBACK contractCode={} requestedDate={} matchType=EXACT asOf={}",
                    canonical,
                    requestedDate,
                    exact.getAsOf());
            return Optional.of(new ViopPriceAtResponse(
                    canonical,
                    requestedEcho,
                    exact.getAsOf(),
                    exact.getPrice(),
                    ViopPriceMatchType.EXACT.name(),
                    SOURCE_LABEL_DERIVATIVE_SNAPSHOT,
                    ViopDataQuality.OK.name()));
        }
        Map.Entry<LocalDate, DerivativeSnapshot> prev = dayLast.lowerEntry(requestedDate);
        if (prev != null && prev.getValue().getPrice() != null && prev.getValue().getPrice().signum() > 0) {
            DerivativeSnapshot p = prev.getValue();
            log.info(
                    "VIOP_PRICE_AT_SNAPSHOT_FALLBACK contractCode={} requestedDate={} matchType=PREVIOUS_AVAILABLE asOf={}",
                    canonical,
                    requestedDate,
                    p.getAsOf());
            return Optional.of(new ViopPriceAtResponse(
                    canonical,
                    requestedEcho,
                    p.getAsOf(),
                    p.getPrice(),
                    ViopPriceMatchType.PREVIOUS_AVAILABLE.name(),
                    SOURCE_LABEL_DERIVATIVE_SNAPSHOT,
                    ViopDataQuality.OK.name()));
        }
        return Optional.empty();
    }

    private void backfillHistoryForPriceAt(
            String canonical,
            MarketViopProperties.IndexEntry entry,
            LocalDate requestedDate,
            ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (requestedDate.isAfter(today)) {
            return;
        }
        int lookback = Math.max(14, viopProperties.getScheduler().getPriceAtBackfillDays());
        LocalDate fetchFrom = requestedDate.minusDays(lookback);
        LocalDate fetchTo = requestedDate.isBefore(today) ? requestedDate.plusDays(1) : today.plusDays(1);
        LocalDateTime from = fetchFrom.atStartOfDay(zone).toLocalDateTime();
        LocalDateTime to = fetchTo.atStartOfDay(zone).minusNanos(1).toLocalDateTime();
        if (!from.isBefore(to)) {
            return;
        }
        int period = viopProperties.getDefaultPeriodMinutes() > 0
                ? viopProperties.getDefaultPeriodMinutes()
                : 60;
        try {
            upsertHistoricalWindowParsed(canonical, entry, from, to, period, zone, System.currentTimeMillis(), "price_at");
            log.info(
                    "VIOP_PRICE_AT_BACKFILL_OK contractCode={} requestedDate={} from={} to={}",
                    canonical,
                    requestedDate,
                    fetchFrom,
                    fetchTo);
        } catch (Exception e) {
            log.warn(
                    "VIOP_PRICE_AT_BACKFILL_FAILED contractCode={} requestedDate={} from={} to={} error={}",
                    canonical,
                    requestedDate,
                    fetchFrom,
                    fetchTo,
                    e.getMessage());
        }
    }

    /**
     * Last stored bar with {@code price_time <= requestedAt}. For whole-calendar-day semantics (last bar on that day
     * in the VIOP timezone, else previous), use {@link #getPriceAt(String, LocalDate)}.
     */
    public ViopPriceAtResponse getPriceAt(String contractCode, LocalDateTime requestedAt) {
        ensureEnabled();
        if (requestedAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        MarketViopProperties.IndexEntry entry = resolveEntry(contractCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or disabled VIOP contract"));
        String canonical = canonical(entry);
        Optional<ViopPriceHistoryEntity> hit =
                priceHistoryRepository.findTopByContractCodeAndPriceTimeLessThanEqualOrderByPriceTimeDesc(
                        canonical, requestedAt);
        if (hit.isEmpty()) {
            log.info("VIOP_PRICE_AT_NOT_FOUND contractCode={} requestedAt={}", canonical, requestedAt);
            return new ViopPriceAtResponse(
                    canonical,
                    requestedAt,
                    null,
                    null,
                    ViopPriceMatchType.NOT_FOUND.name(),
                    SOURCE_LABEL_DB,
                    ViopDataQuality.OK.name());
        }
        ViopPriceHistoryEntity pick = hit.get();
        String matchType =
                pick.getPriceTime().equals(requestedAt)
                        ? ViopPriceMatchType.EXACT.name()
                        : ViopPriceMatchType.PREVIOUS_AVAILABLE.name();
        log.info(
                "VIOP_PRICE_AT_MATCH contractCode={} requestedAt={} matchType={} priceTime={}",
                canonical,
                requestedAt,
                matchType,
                pick.getPriceTime());
        return new ViopPriceAtResponse(
                canonical,
                requestedAt,
                pick.getPriceTime(),
                pick.getPrice(),
                matchType,
                SOURCE_LABEL_DB,
                ViopDataQuality.OK.name());
    }

    @Transactional
    public ViopMarketSnapshotDto refreshSnapshot(String contractCode) {
        ensureEnabled();
        MarketViopProperties.IndexEntry entry = resolveEntry(contractCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or disabled VIOP contract"));
        String canonical = canonical(entry);
        stringRedisTemplate.delete(snapshotRedisKey(canonical));
        return fetchSnapshotFromProvider(entry, canonical, snapshotRedisKey(canonical));
    }

    @Transactional
    public ViopHistoryResponse refreshHistory(String contractCode, LocalDateTime from, LocalDateTime to, int period) {
        ensureEnabled();
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`");
        }
        MarketViopProperties.IndexEntry entry = resolveEntry(contractCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or disabled VIOP contract"));
        String canonical = canonical(entry);
        int periodM = period > 0 ? period : viopProperties.getDefaultPeriodMinutes();
        stringRedisTemplate.delete(historyRedisKey(canonical, from, to, periodM));
        return refreshHistoryFromProvider(entry, canonical, from, to, periodM);
    }

    @Transactional
    public void refreshAllSnapshots() {
        runSnapshotRefreshForAllContracts(false);
    }

    /** Zamanlayıcı: kontrat bazlı süre ve özet logları. Self-invocation yüzünden iç {@code refreshSnapshot} proxy'den geçmez; dış transaction şart. */
    @Transactional
    public void refreshAllSnapshotsWithSchedulerLogging() {
        runSnapshotRefreshForAllContracts(true);
    }

    private void runSnapshotRefreshForAllContracts(boolean schedulerLogging) {
        long runStarted = System.currentTimeMillis();
        String provider = viopProperties.getProvider();
        var contracts = enabledEntries().toList();
        int success = 0;
        int failure = 0;
        for (MarketViopProperties.IndexEntry e : contracts) {
            String code = e.getContractCode();
            long t0 = System.currentTimeMillis();
            try {
                refreshSnapshot(code);
                success++;
                if (schedulerLogging) {
                    log.info(
                            "VIOP_SCHEDULER_SNAPSHOT contractCode={} provider={} operation=snapshot status=OK durationMs={}",
                            code,
                            provider,
                            System.currentTimeMillis() - t0);
                }
            } catch (Exception ex) {
                failure++;
                if (schedulerLogging) {
                    log.warn(
                            "VIOP_SCHEDULER_SNAPSHOT contractCode={} provider={} operation=snapshot status=FAIL durationMs={} error={}",
                            code,
                            provider,
                            System.currentTimeMillis() - t0,
                            ex.getMessage(),
                            ex);
                } else {
                    log.warn("VIOP scheduler snapshot skip contract={} error={}", code, ex.getMessage());
                }
            }
        }
        if (schedulerLogging) {
            log.info(
                    "VIOP_SNAPSHOT_SCHEDULER_COMPLETED totalContracts={} successCount={} failureCount={} durationMs={}",
                    contracts.size(),
                    success,
                    failure,
                    System.currentTimeMillis() - runStarted);
        }
    }

    @Transactional
    public void refreshRecentHistoryForAllContracts() {
        runRecentHistoryRefreshForAllContracts(false);
    }

    /** Zamanlayıcı: DB öncelikli incremental materialize; admin zorunlu yenileme için {@link #refreshHistory}. */
    @Transactional
    public void refreshRecentHistoryForAllContractsWithSchedulerLogging() {
        runRecentHistoryRefreshForAllContracts(true);
    }

    private void runRecentHistoryRefreshForAllContracts(boolean schedulerLogging) {
        ensureEnabled();
        long runStarted = System.currentTimeMillis();
        String provider = viopProperties.getProvider();
        MarketViopProperties.Scheduler sched = viopProperties.getScheduler();
        ZoneId zone = ZoneId.of(viopProperties.getTimezone());
        LocalDateTime to = LocalDateTime.now(zone);
        LocalDateTime from = to.minusDays(sched.getHistoryLookbackDays());
        int chunkDays = Math.max(1, sched.getHistoryChunkDays());
        int period = sched.getPeriodMinutes() > 0 ? sched.getPeriodMinutes() : viopProperties.getDefaultPeriodMinutes();
        var contracts = enabledEntries().toList();
        int success = 0;
        int failure = 0;
        for (MarketViopProperties.IndexEntry e : contracts) {
            String code = e.getContractCode();
            long t0 = System.currentTimeMillis();
            int pointCount = 0;
            boolean anyChunkFailed = false;
            LocalDateTime chunkFrom = from;
            while (chunkFrom.isBefore(to)) {
                LocalDateTime chunkTo = chunkFrom.plusDays(chunkDays);
                if (chunkTo.isAfter(to)) {
                    chunkTo = to;
                }
                long tChunk = System.currentTimeMillis();
                try {
                    int n =
                            materializeSchedulerHistoryChunk(
                                    code, e, chunkFrom, chunkTo, period, zone, schedulerLogging);
                    pointCount += n;
                } catch (Exception ex) {
                    anyChunkFailed = true;
                    if (schedulerLogging) {
                        log.warn(
                                "VIOP_SCHEDULER_HISTORY contractCode={} provider={} operation=history status=FAIL "
                                        + "from={} to={} durationMs={} error={}",
                                code,
                                provider,
                                chunkFrom,
                                chunkTo,
                                System.currentTimeMillis() - tChunk,
                                ex.getMessage(),
                                ex);
                    } else {
                        log.warn(
                                "VIOP scheduler history skip contract={} from={} to={} error={}",
                                code,
                                chunkFrom,
                                chunkTo,
                                ex.getMessage());
                    }
                }
                chunkFrom = chunkTo.plusNanos(1);
            }
            long durationMs = System.currentTimeMillis() - t0;
            if (anyChunkFailed) {
                failure++;
                if (schedulerLogging) {
                    log.warn(
                            "VIOP_SCHEDULER_HISTORY contractCode={} provider={} operation=history status=FAIL "
                                    + "pointCount={} durationMs={} (one or more chunks failed)",
                            code,
                            provider,
                            pointCount,
                            durationMs);
                }
            } else {
                success++;
                if (schedulerLogging) {
                    log.info(
                            "VIOP_SCHEDULER_HISTORY contractCode={} provider={} operation=history status=OK "
                                    + "pointCount={} durationMs={}",
                            code,
                            provider,
                            pointCount,
                            durationMs);
                }
            }
        }
        if (schedulerLogging) {
            log.info(
                    "VIOP_HISTORY_SCHEDULER_COMPLETED totalContracts={} successCount={} failureCount={} durationMs={}",
                    contracts.size(),
                    success,
                    failure,
                    System.currentTimeMillis() - runStarted);
        }
    }

    /**
     * Zamanlayıcı: DB'de pencere zaten güncelse İş Yatırım çağrısı yapılmaz; eksik baş taraf veya kuyruk varsa
     * yalnız o aralık çekilir ({@code insertIgnore} mevcut mumları tekrar yazmaz).
     */
    private int materializeSchedulerHistoryChunk(
            String contractCode,
            MarketViopProperties.IndexEntry entry,
            LocalDateTime chunkFrom,
            LocalDateTime chunkTo,
            int period,
            ZoneId zone,
            boolean schedulerLogging)
            throws Exception {
        String canonical = canonical(entry);
        int pm = Math.max(1, period);
        List<ViopPriceHistoryEntity> rows =
                priceHistoryRepository.findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(
                        canonical, chunkFrom, chunkTo);
        if (rows.isEmpty()) {
            long t0 = System.currentTimeMillis();
            upsertHistoricalWindowParsed(
                    canonical, entry, chunkFrom, chunkTo, period, zone, t0, "scheduler_full_window");
            rows =
                    priceHistoryRepository.findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(
                            canonical, chunkFrom, chunkTo);
            return rows.size();
        }
        LocalDateTime firstPt = rows.get(0).getPriceTime();
        if (firstPt.isAfter(chunkFrom.plusMinutes(pm))) {
            if (schedulerLogging) {
                log.info(
                        "VIOP_SCHEDULER_HISTORY_CHUNK_HEAD_GAP contractCode={} chunkFrom={} firstDbPriceTime={} period={}",
                        canonical,
                        chunkFrom,
                        firstPt,
                        period);
            }
            LocalDateTime headTo = firstPt.minusNanos(1L);
            if (chunkFrom.isBefore(headTo)) {
                long t0 = System.currentTimeMillis();
                upsertHistoricalWindowParsed(
                        canonical, entry, chunkFrom, headTo, period, zone, t0, "scheduler_head_gap");
                rows =
                        priceHistoryRepository.findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(
                                canonical, chunkFrom, chunkTo);
            }
        }
        if (rows.isEmpty()) {
            return 0;
        }
        LocalDateTime latestPt = rows.get(rows.size() - 1).getPriceTime();
        if (!historyTailNeedsRefresh(latestPt, chunkTo, period)) {
            if (schedulerLogging) {
                log.info(
                        "VIOP_SCHEDULER_HISTORY_CHUNK_SKIP_DB_WARM contractCode={} chunkFrom={} chunkTo={} "
                                + "latestDbPriceTime={} period={}",
                        canonical,
                        chunkFrom,
                        chunkTo,
                        latestPt,
                        period);
            }
            return rows.size();
        }
        LocalDateTime fetchFrom = latestPt.plusNanos(1L);
        if (!fetchFrom.isBefore(chunkTo)) {
            return rows.size();
        }
        long t0 = System.currentTimeMillis();
        upsertHistoricalWindowParsed(
                canonical, entry, fetchFrom, chunkTo, period, zone, t0, "scheduler_tail_merge");
        rows =
                priceHistoryRepository.findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(
                        canonical, chunkFrom, chunkTo);
        return rows.size();
    }

    private ViopHistoryResponse refreshHistoryFromProvider(
            MarketViopProperties.IndexEntry entry,
            String canonical,
            LocalDateTime from,
            LocalDateTime to,
            int period) {
        ZoneId zone = ZoneId.of(viopProperties.getTimezone());
        String cacheKey = historyRedisKey(canonical, from, to, period);
        long t0 = System.currentTimeMillis();
        try {
            IsYatirimViopHistoricalParser.ParsedHistorical parsed =
                    upsertHistoricalWindowParsed(canonical, entry, from, to, period, zone, t0, "refresh_admin");
            List<ViopPriceHistoryEntity> rows =
                    priceHistoryRepository.findByContractCodeAndPriceTimeBetweenOrderByPriceTimeAsc(
                            canonical, from, to);
            String quality =
                    rows.isEmpty() && parsed.rows().isEmpty()
                            ? ViopDataQuality.EMPTY_RESPONSE.name()
                            : ViopDataQuality.OK.name();
            ViopHistoryResponse dto =
                    buildHistory(entry, rows, period, from, to, parsed.providerTimestamp(), quality, SOURCE_LABEL_PROVIDER);
            writeHistoryRedis(cacheKey, dto);
            return dto;
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.warn(
                    "VIOP history refresh failed contract={} from={} to={} period={} error={} rootCause={}",
                    canonical,
                    from,
                    to,
                    period,
                    e.getMessage(),
                    root != e ? root.getClass().getSimpleName() + ": " + root.getMessage() : e.getClass().getSimpleName(),
                    e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VIOP history fetch failed", e);
        }
    }

    private ViopMarketSnapshotDto fetchSnapshotFromProvider(
            MarketViopProperties.IndexEntry entry, String canonical, String cacheKey) {
        try {
            String raw = isYatirimViopClient.fetchSnapshot(canonical);
            IsYatirimViopSnapshotParser.ParsedSnapshot parsed = snapshotParser.parse(raw, canonical);
            if (parsed.dataQuality() != ViopDataQuality.OK || parsed.updateDate() == null) {
                return new ViopMarketSnapshotDto(
                        canonical,
                        entry.getUnderlying(),
                        entry.getDisplayName(),
                        entry.getContractName(),
                        entry.getMaturityMonth(),
                        entry.getMaturityYear(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        SOURCE_LABEL_PROVIDER,
                        viopProperties.getDelayMinutes(),
                        parsed.dataQuality().name());
            }
            ZoneId zone = ZoneId.of(viopProperties.getTimezone());
            LocalDateTime updateLocal = IsYatirimViopSnapshotParser.toLocalDateTime(parsed.updateDate(), zone);
            ViopSnapshotEntity entity = snapshotRepository
                    .findByContractCodeAndUpdateDateAndSource(canonical, updateLocal, SOURCE_ISYATIRIM)
                    .orElseGet(ViopSnapshotEntity::new);
            fillSnapshotEntity(entity, entry, canonical, updateLocal, parsed);
            snapshotRepository.save(entity);
            log.info(
                    "VIOP_SNAPSHOT_SAVE_SUCCESS contractCode={} updateDate={} source={}",
                    canonical,
                    updateLocal,
                    SOURCE_ISYATIRIM);
            ViopMarketSnapshotDto dto =
                    fromParsed(entry, canonical, parsed, SOURCE_LABEL_PROVIDER, ViopDataQuality.OK.name());
            writeSnapshotRedis(cacheKey, dto);
            return dto;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("VIOP snapshot upstream failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VIOP snapshot fetch failed", e);
        }
    }

    private static void fillSnapshotEntity(
            ViopSnapshotEntity entity,
            MarketViopProperties.IndexEntry entry,
            String canonical,
            LocalDateTime updateLocal,
            IsYatirimViopSnapshotParser.ParsedSnapshot p) {
        entity.setContractCode(canonical);
        entity.setUnderlying(entry.getUnderlying());
        entity.setAssetClass(entry.getAssetClass());
        entity.setSegment(entry.getSegment());
        entity.setUpdateDate(updateLocal);
        entity.setBid(p.bid());
        entity.setAsk(p.ask());
        entity.setLow(p.low());
        entity.setHigh(p.high());
        entity.setLast(p.last());
        entity.setDayClose(p.dayClose());
        entity.setOpenPrice(p.openPrice());
        entity.setChangeAmount(p.changeAmount());
        entity.setChangePercent(p.changePercent());
        entity.setQuantity(p.quantity());
        entity.setVolume(p.volume());
        entity.setSettlement(p.settlement());
        entity.setPreSettlement(p.preSettlement());
        entity.setLimitUp(p.limitUp());
        entity.setLimitDown(p.limitDown());
        entity.setPriceStep(p.priceStep());
        entity.setInitialMargin(p.initialMargin());
        entity.setWeekLow(p.weekLow());
        entity.setWeekHigh(p.weekHigh());
        entity.setWeekClose(p.weekClose());
        entity.setMonthLow(p.monthLow());
        entity.setMonthHigh(p.monthHigh());
        entity.setMonthClose(p.monthClose());
        entity.setYearClose(p.yearClose());
        entity.setPrevYearClose(p.prevYearClose());
        entity.setSource(SOURCE_ISYATIRIM);
    }

    private ViopMarketSnapshotDto fromEntity(
            ViopSnapshotEntity e, MarketViopProperties.IndexEntry entry, String sourceLabel, String dataQuality) {
        ZoneId zone = ZoneId.of(viopProperties.getTimezone());
        OffsetDateTime updateOdt =
                e.getUpdateDate() == null ? null : e.getUpdateDate().atZone(zone).toOffsetDateTime();
        return new ViopMarketSnapshotDto(
                e.getContractCode(),
                e.getUnderlying(),
                entry.getDisplayName(),
                entry.getContractName(),
                entry.getMaturityMonth(),
                entry.getMaturityYear(),
                updateOdt,
                e.getBid(),
                e.getAsk(),
                e.getLow(),
                e.getHigh(),
                e.getLast(),
                e.getDayClose(),
                e.getOpenPrice(),
                e.getChangeAmount(),
                e.getChangePercent(),
                e.getQuantity(),
                e.getVolume(),
                e.getSettlement(),
                e.getPreSettlement(),
                e.getLimitUp(),
                e.getLimitDown(),
                e.getPriceStep(),
                e.getInitialMargin(),
                e.getWeekLow(),
                e.getWeekHigh(),
                e.getWeekClose(),
                e.getMonthLow(),
                e.getMonthHigh(),
                e.getMonthClose(),
                e.getYearClose(),
                e.getPrevYearClose(),
                sourceLabel,
                viopProperties.getDelayMinutes(),
                dataQuality);
    }

    private ViopMarketSnapshotDto fromParsed(
            MarketViopProperties.IndexEntry entry,
            String canonical,
            IsYatirimViopSnapshotParser.ParsedSnapshot p,
            String sourceLabel,
            String dataQuality) {
        return new ViopMarketSnapshotDto(
                canonical,
                entry.getUnderlying(),
                entry.getDisplayName(),
                entry.getContractName(),
                entry.getMaturityMonth(),
                entry.getMaturityYear(),
                p.updateDate(),
                p.bid(),
                p.ask(),
                p.low(),
                p.high(),
                p.last(),
                p.dayClose(),
                p.openPrice(),
                p.changeAmount(),
                p.changePercent(),
                p.quantity(),
                p.volume(),
                p.settlement(),
                p.preSettlement(),
                p.limitUp(),
                p.limitDown(),
                p.priceStep(),
                p.initialMargin(),
                p.weekLow(),
                p.weekHigh(),
                p.weekClose(),
                p.monthLow(),
                p.monthHigh(),
                p.monthClose(),
                p.yearClose(),
                p.prevYearClose(),
                sourceLabel,
                viopProperties.getDelayMinutes(),
                dataQuality);
    }

    private ViopHistoryResponse buildHistory(
            MarketViopProperties.IndexEntry entry,
            List<ViopPriceHistoryEntity> rows,
            int period,
            LocalDateTime from,
            LocalDateTime to,
            OffsetDateTime providerTimestamp,
            String dataQuality,
            String historySourceLabel) {
        ZoneId zone = ZoneId.of(viopProperties.getTimezone());
        List<ViopPricePoint> points = new ArrayList<>(rows.size());
        for (ViopPriceHistoryEntity r : rows) {
            Instant inst = r.getPriceTime().atZone(zone).toInstant();
            points.add(new ViopPricePoint(r.getContractCode(), inst, r.getPrice(), r.getSource(), r.getPeriodMinutes()));
        }
        return new ViopHistoryResponse(
                canonical(entry),
                entry.getUnderlying(),
                entry.getDisplayName(),
                entry.getContractName(),
                entry.getMaturityMonth(),
                entry.getMaturityYear(),
                entry.getAssetClass(),
                entry.getSegment(),
                historySourceLabel,
                viopProperties.getDelayMinutes(),
                period,
                entry.getChartType() != null ? entry.getChartType() : "PRICE_SERIES",
                dataQuality,
                providerTimestamp,
                from,
                to,
                points);
    }

    private ViopMarketContractDto fromLegacy(ViopContractResponse c) {
        return new ViopMarketContractDto(
                c.contractCode(),
                c.underlying(),
                c.underlying(),
                c.contractCode(),
                0,
                0,
                "UNKNOWN",
                "UNKNOWN",
                "PRICE_SERIES",
                true,
                false,
                SOURCE_LABEL_DB,
                viopProperties.getDelayMinutes());
    }

    private ViopMarketContractDto toContractDto(MarketViopProperties.IndexEntry e, YearMonth nowYm, boolean includeExpired) {
        YearMonth mat = YearMonth.of(e.getMaturityYear(), e.getMaturityMonth());
        boolean expired = mat.isBefore(nowYm);
        if (expired && !includeExpired) {
            return null;
        }
        return new ViopMarketContractDto(
                canonical(e),
                e.getUnderlying(),
                e.getDisplayName(),
                e.getContractName(),
                e.getMaturityMonth(),
                e.getMaturityYear(),
                e.getAssetClass(),
                e.getSegment(),
                e.getChartType(),
                e.isEnabled(),
                expired,
                SOURCE_LABEL_PROVIDER,
                viopProperties.getDelayMinutes());
    }

    private Stream<MarketViopProperties.IndexEntry> enabledEntries() {
        List<MarketViopProperties.IndexEntry> index = viopProperties.getWhitelist().getIndex();
        List<MarketViopProperties.IndexEntry> fx = viopProperties.getWhitelist().getFx();
        List<MarketViopProperties.IndexEntry> pm = viopProperties.getWhitelist().getPreciousMetal();
        List<MarketViopProperties.IndexEntry> eq = viopProperties.getWhitelist().getEquity();
        Stream<MarketViopProperties.IndexEntry> indexStream = index == null ? Stream.empty() : index.stream();
        Stream<MarketViopProperties.IndexEntry> fxStream = fx == null ? Stream.empty() : fx.stream();
        Stream<MarketViopProperties.IndexEntry> pmStream = pm == null ? Stream.empty() : pm.stream();
        Stream<MarketViopProperties.IndexEntry> eqStream = eq == null ? Stream.empty() : eq.stream();
        return Stream.concat(Stream.concat(Stream.concat(indexStream, fxStream), pmStream), eqStream)
                .filter(MarketViopProperties.IndexEntry::isEnabled);
    }

    private Optional<MarketViopProperties.IndexEntry> resolveEntry(String requested) {
        if (requested == null || requested.isBlank()) {
            return Optional.empty();
        }
        String u = requested.trim().toUpperCase();
        String withF = u.startsWith("F_") ? u : "F_" + u;
        String noF = u.startsWith("F_") ? u.substring(2) : u;
        return enabledEntries()
                .filter(e -> {
                    if (e.getContractCode() == null) {
                        return false;
                    }
                    String c = e.getContractCode().trim().toUpperCase();
                    return c.equals(u) || c.equals(withF) || c.equals(noF) || c.equals("F_" + noF);
                })
                .findFirst();
    }

    private static String canonical(MarketViopProperties.IndexEntry e) {
        return e.getContractCode().trim().toUpperCase();
    }

    private Set<String> contractCodeAliases(String canonical) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (canonical == null || canonical.isBlank()) {
            return out;
        }
        String u = canonical.trim().toUpperCase();
        out.add(u);
        String stripped = u.startsWith("F_") ? u.substring(2) : u;
        if (!stripped.isBlank()) {
            out.add(stripped);
            out.add("F_" + stripped);
        }
        return out;
    }

    private void ensureEnabled() {
        if (!viopProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VIOP integration disabled");
        }
    }

    private void writeSnapshotRedis(String cacheKey, ViopMarketSnapshotDto dto) {
        try {
            stringRedisTemplate
                    .opsForValue()
                    .set(cacheKey, REDIS_JSON.writeValueAsString(dto), Duration.ofSeconds(viopProperties.getCacheTtlSeconds()));
        } catch (Exception e) {
            log.warn("VIOP snapshot cache write failed {}: {}", cacheKey, e.getMessage());
        }
    }

    private void writeHistoryRedis(String cacheKey, ViopHistoryResponse dto) {
        try {
            stringRedisTemplate
                    .opsForValue()
                    .set(cacheKey, REDIS_JSON.writeValueAsString(dto), Duration.ofSeconds(viopProperties.getCacheTtlSeconds()));
        } catch (Exception e) {
            log.warn("VIOP history cache write failed {}: {}", cacheKey, e.getMessage());
        }
    }

    private static String snapshotRedisKey(String canonical) {
        return "market:viop:snapshot:" + canonical;
    }

    private static String historyRedisKey(String canonical, LocalDateTime from, LocalDateTime to, int period) {
        return "market:viop:history:" + canonical + ":" + from + ":" + to + ":" + period;
    }
}
