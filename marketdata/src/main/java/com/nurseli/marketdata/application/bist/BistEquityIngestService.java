package com.nurseli.marketdata.application.bist;

import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.bist.BistEquityDailyPrice;
import com.nurseli.marketdata.infrastructure.bist.BistEquityMarketProvider;
import com.nurseli.marketdata.infrastructure.bist.BistProviderResult;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BistEquityIngestService {

    private final BistEquityMarketProvider bistEquityMarketProvider;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final BistSymbolCatalog bistSymbolCatalog;
    private final BistEquityPersistenceMapper bistEquityPersistenceMapper;
    private final BistProperties bistProperties;

    @Transactional
    public BistEquityIngestSymbolResult ingestHistory(String symbol, LocalDate from, LocalDate to) {
        String sym = symbol != null ? symbol.trim().toUpperCase() : "";
        if (!bistSymbolCatalog.isSupported(sym)) {
            return BistEquityIngestSymbolResult.empty(sym, List.of("unsupported_symbol"));
        }
        if (from == null || to == null || to.isBefore(from)) {
            return BistEquityIngestSymbolResult.empty(sym, List.of("invalid_date_range"));
        }

        int chunkDays = Math.max(7, bistProperties.getHistoryFetchChunkDays());
        long spanDays = ChronoUnit.DAYS.between(from, to) + 1L;
        if (spanDays <= chunkDays) {
            return ingestHistoryWindow(sym, from, to);
        }

        log.info("[BIST_DAILY_INGEST] chunked symbol={} from={} to={} spanDays={} chunkDays={}", sym, from, to, spanDays, chunkDays);
        int totalFetched = 0;
        int totalWritten = 0;
        int totalSkipped = 0;
        List<String> warnings = new ArrayList<>();
        boolean allOk = true;
        LocalDate cursor = from;
        int chunkIndex = 0;
        while (!cursor.isAfter(to)) {
            LocalDate chunkEnd = cursor.plusDays((long) chunkDays - 1L);
            if (chunkEnd.isAfter(to)) {
                chunkEnd = to;
            }
            chunkIndex++;
            BistEquityIngestSymbolResult part = ingestHistoryWindow(sym, cursor, chunkEnd);
            totalFetched += part.rowsFetched();
            totalWritten += part.rowsWritten();
            totalSkipped += part.rowsSkipped();
            warnings.addAll(part.warnings());
            if (!part.success()) {
                allOk = false;
                log.warn(
                        "[BIST_DAILY_INGEST] chunk_failed symbol={} chunk={} from={} to={}",
                        sym,
                        chunkIndex,
                        cursor,
                        chunkEnd);
                break;
            }
            cursor = chunkEnd.plusDays(1L);
            if (!cursor.isAfter(to) && bistProperties.getDelayMsBetweenHistoryChunks() > 0) {
                sleepQuietly(bistProperties.getDelayMsBetweenHistoryChunks());
            }
        }
        return BistEquityIngestSymbolResult.of(sym, totalFetched, totalWritten, totalSkipped, allOk, warnings);
    }

    private static void sleepQuietly(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Tek HisseTekil penceresi: {@code from..to} dahil en fazla {@code chunkDays} civarı gün.
     */
    private BistEquityIngestSymbolResult ingestHistoryWindow(String sym, LocalDate from, LocalDate to) {
        BistProviderResult<List<BistEquityDailyPrice>> result;
        try {
            result = bistEquityMarketProvider.fetchHistory(sym, from, to);
        } catch (Exception ex) {
            log.warn("[BIST_DAILY_INGEST] fetch failed symbol={} reason={}", sym, ex.getMessage());
            return BistEquityIngestSymbolResult.empty(sym, List.of("provider_exception:" + ex.getClass().getSimpleName()));
        }

        List<String> warnings = new ArrayList<>(BistProviderResult.copyWarnings(result.warnings()));
        if (!result.success() || result.data() == null) {
            if (result.error() != null && result.error().message() != null) {
                warnings.add(result.error().message());
            }
            return BistEquityIngestSymbolResult.of(sym, 0, 0, 0, false, warnings);
        }

        List<BistEquityDailyPrice> rows = result.data();
        int fetched = rows.size();
        int written = 0;
        int skipped = 0;

        for (BistEquityDailyPrice row : rows) {
            if (row == null || row.date() == null) {
                skipped++;
                continue;
            }
            String src = bistEquityPersistenceMapper.resolveSource(row);
            var ts = bistEquityPersistenceMapper.toHistoryTimestamp(row);
            if (row.adjustedClose() == null && row.rawClose() == null && row.adjustedAverage() == null) {
                skipped++;
                continue;
            }

            var existing = marketPriceHistoryRepository.findBySymbolAndSourceAndTimestamp(sym, src, ts);
            if (existing.isPresent()) {
                bistEquityPersistenceMapper.copyOnto(existing.get(), row);
                marketPriceHistoryRepository.save(existing.get());
                written++;
            } else {
                MarketPriceHistory entity = bistEquityPersistenceMapper.toNewEntity(row);
                marketPriceHistoryRepository.save(entity);
                written++;
            }
        }

        boolean ok = result.success();
        return BistEquityIngestSymbolResult.of(sym, fetched, written, skipped, ok, warnings);
    }
}
