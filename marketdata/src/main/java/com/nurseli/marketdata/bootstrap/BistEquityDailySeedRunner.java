package com.nurseli.marketdata.bootstrap;

import com.nurseli.marketdata.api.dto.BistBackfillRequest;
import com.nurseli.marketdata.application.bist.BistEquityBackfillService;
import com.nurseli.marketdata.application.bist.BistEquityDailyConstants;
import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolMetadata;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Boş veya <i>sığ</i> veritabanında BIST günlük satırlarını hedef derinliğe getirir (Docker / ilk kurulum).
 * Daha önce kısa pencerede doldurulmuş DB'lerde de en eski satır {@code seedLookbackDays}'den yeniyse yeniden çeker.
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
        LocalDate oldest =
                minTsOpt.map(LocalDateTime::toLocalDate).orElse(null);
        boolean depthInsufficient =
                minTsOpt.isEmpty() || oldest == null || oldest.isAfter(targetFrom);

        if (existing > 0 && !depthInsufficient) {
            log.info("[BIST_DAILY_SEED] skip depth_ok rows={} oldest={} targetFrom={}", existing, oldest, targetFrom);
            return;
        }
        if (existing > 0) {
            log.info(
                    "[BIST_DAILY_SEED] depth_repair rows={} oldest={} targetFrom={} lookbackDays={}",
                    existing,
                    oldest,
                    targetFrom,
                    lookbackDays);
        }

        List<String> seedSymbols = resolveSeedSymbols();
        if (seedSymbols.isEmpty()) {
            log.warn("[BIST_DAILY_SEED] skip no symbols (configure market.bist.symbols or rely on catalog)");
            return;
        }

        LocalDate from = to.minusDays(lookbackDays);
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
