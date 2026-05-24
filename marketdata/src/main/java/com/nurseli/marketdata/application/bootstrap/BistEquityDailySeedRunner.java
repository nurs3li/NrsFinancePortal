package com.nurseli.marketdata.application.bootstrap;

import com.nurseli.marketdata.api.dto.BistBackfillRequest;
import com.nurseli.marketdata.application.bist.BistEquityBackfillService;
import com.nurseli.marketdata.application.bist.BistEquityDailyConstants;
import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolMetadata;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Boş veya <i>sığ</i> veritabanında BIST günlük satırlarını hedef derinliğe getirir (Docker / ilk kurulum).
 * Daha önce kısa pencerede doldurulmuş DB'lerde de en eski satır {@code seedLookbackDays}'den yeniyse yeniden çeker.
 * Kataloğa yeni eklenen semboller (DB'de hiç satır yok) mevcut DB dolu olsa bile ayrıca doldurulur.
 */
@Component
@Order(2000)
@RequiredArgsConstructor
@Slf4j
public class BistEquityDailySeedRunner implements ApplicationRunner {

    private final BistProperties bistProperties;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final BistEquityBackfillService bistEquityBackfillService;
    private final BistSymbolCatalog bistSymbolCatalog;

    @Override
    public void run(ApplicationArguments args) {
        if (!bistProperties.isEnabled() || !bistProperties.isSeedOnStart()) {
            return;
        }
        ZoneId zone = ZoneId.of(bistProperties.getSchedulerZone());
        LocalDate to = LocalDate.now(zone);
        int lookbackDays = Math.max(7, bistProperties.getSeedLookbackDays());
        LocalDate targetFrom = to.minusDays(lookbackDays);

        String source = BistEquityDailyConstants.HISTORY_SOURCE;
        long existing = marketPriceHistoryRepository.countBySource(source);
        Optional<LocalDateTime> minTsOpt = marketPriceHistoryRepository.findMinTimestampBySource(source);
        Optional<LocalDateTime> maxTsOpt = marketPriceHistoryRepository.findMaxTimestampBySource(source);
        LocalDate oldest =
                minTsOpt.map(LocalDateTime::toLocalDate).orElse(null);
        LocalDate newest =
                maxTsOpt.map(LocalDateTime::toLocalDate).orElse(null);
        boolean depthInsufficient =
                minTsOpt.isEmpty() || oldest == null || oldest.isAfter(targetFrom);
        int staleTailDays = Math.max(1, bistProperties.getStaleTailDays());
        LocalDate tailCutoff = to.minusDays(staleTailDays);
        boolean tailStale = newest == null || !newest.isAfter(tailCutoff);

        List<String> seedSymbols = resolveSeedSymbols();
        if (seedSymbols.isEmpty()) {
            log.warn("[BIST_DAILY_SEED] skip no symbols (configure market.bist.symbols or rely on catalog)");
            return;
        }

        Map<String, LocalDate> symbolGaps = findSymbolBackfillFrom(seedSymbols, targetFrom, to, tailCutoff);
        boolean hasSymbolGaps = !symbolGaps.isEmpty();

        if (existing > 0 && !depthInsufficient && !tailStale && !hasSymbolGaps) {
            log.info(
                    "[BIST_DAILY_SEED] skip depth_ok rows={} oldest={} newest={} targetFrom={}",
                    existing,
                    oldest,
                    newest,
                    targetFrom);
            return;
        }
        if (hasSymbolGaps) {
            log.info(
                    "[BIST_DAILY_SEED] symbol_gap_repair symbols={} targetFrom={} to={}",
                    symbolGaps.keySet(),
                    targetFrom,
                    to);
            runPerSymbolBackfill(symbolGaps, to);
            return;
        }
        if (existing > 0 && tailStale) {
            log.info(
                    "[BIST_DAILY_SEED] tail_repair rows={} newest={} tailCutoff={} staleTailDays={}",
                    existing,
                    newest,
                    tailCutoff,
                    staleTailDays);
        }
        if (existing > 0) {
            log.info(
                    "[BIST_DAILY_SEED] depth_repair rows={} oldest={} targetFrom={} lookbackDays={}",
                    existing,
                    oldest,
                    targetFrom,
                    lookbackDays);
        }

        LocalDate from;
        if (existing > 0 && !depthInsufficient && tailStale && newest != null) {
            from = newest.plusDays(1);
            if (from.isBefore(targetFrom)) {
                from = targetFrom;
            }
        } else {
            from = targetFrom;
        }
        BistBackfillRequest req = new BistBackfillRequest();
        req.setSymbols(seedSymbols);
        req.setFrom(from);
        req.setTo(to);
        log.info("[BIST_DAILY_SEED] starting symbols={} from={} to={}", seedSymbols, from, to);
        try {
            bistEquityBackfillService.runBackfill(req);
        } catch (Exception ex) {
            log.warn("[BIST_DAILY_SEED] failed reason={}", ex.getMessage());
        }
    }

    /**
     * Sembol başına ingest başlangıç tarihi: hiç satır yok veya derinlik yetersiz → {@code targetFrom};
     * yalnızca kuyruk bayat → son günden sonrası.
     */
    private Map<String, LocalDate> findSymbolBackfillFrom(
            List<String> symbols, LocalDate targetFrom, LocalDate to, LocalDate tailCutoff) {
        String source = BistEquityDailyConstants.HISTORY_SOURCE;
        Map<String, LocalDate> out = new LinkedHashMap<>();
        for (String sym : symbols) {
            long count = marketPriceHistoryRepository.countBySymbolAndSource(sym, source);
            if (count == 0) {
                out.put(sym, targetFrom);
                continue;
            }
            Optional<MarketPriceHistory> oldestRow =
                    marketPriceHistoryRepository.findTopBySymbolAndSourceOrderByTimestampAsc(sym, source);
            LocalDate symbolOldest =
                    oldestRow.map(r -> r.getTimestamp().toLocalDate()).orElse(null);
            if (symbolOldest == null || symbolOldest.isAfter(targetFrom)) {
                out.put(sym, targetFrom);
                continue;
            }
            Optional<MarketPriceHistory> latestRow =
                    marketPriceHistoryRepository.findTopBySymbolAndSourceOrderByTimestampDesc(sym, source);
            LocalDate symbolNewest =
                    latestRow.map(r -> r.getTimestamp().toLocalDate()).orElse(null);
            if (symbolNewest == null || !symbolNewest.isAfter(tailCutoff)) {
                LocalDate from = symbolNewest != null ? symbolNewest.plusDays(1) : targetFrom;
                if (from.isBefore(targetFrom)) {
                    from = targetFrom;
                }
                if (!from.isAfter(to)) {
                    out.put(sym, from);
                }
            }
        }
        return out;
    }

    private void runPerSymbolBackfill(Map<String, LocalDate> symbolFrom, LocalDate to) {
        for (Map.Entry<String, LocalDate> e : symbolFrom.entrySet()) {
            BistBackfillRequest req = new BistBackfillRequest();
            req.setSymbols(List.of(e.getKey()));
            req.setFrom(e.getValue());
            req.setTo(to);
            log.info("[BIST_DAILY_SEED] symbol={} from={} to={}", e.getKey(), e.getValue(), to);
            try {
                bistEquityBackfillService.runBackfill(req);
            } catch (Exception ex) {
                log.warn("[BIST_DAILY_SEED] symbol={} failed reason={}", e.getKey(), ex.getMessage());
            }
        }
    }

    private List<String> resolveSeedSymbols() {
        int cap = Math.max(1, bistProperties.getSeedMaxSymbols());
        List<String> configured = bistProperties.getSymbols();
        List<String> out = new ArrayList<>();
        if (configured != null && !configured.isEmpty()) {
            for (String s : configured) {
                if (s == null || s.isBlank()) {
                    continue;
                }
                out.add(s.trim().toUpperCase(Locale.ROOT));
                if (out.size() >= cap) {
                    break;
                }
            }
            return out;
        }
        for (BistSymbolMetadata m : bistSymbolCatalog.getAll()) {
            out.add(m.symbol());
            if (out.size() >= cap) {
                break;
            }
        }
        return out;
    }
}
