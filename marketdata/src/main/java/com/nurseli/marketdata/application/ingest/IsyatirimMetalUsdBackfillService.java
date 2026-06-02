package com.nurseli.marketdata.application.ingest;

import com.nurseli.marketdata.api.dto.IsyatirimMetalBackfillResponse;
import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class IsyatirimMetalUsdBackfillService {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    private final MarketMetalsIsyatirimProperties properties;
    private final IsYatirimMetalUsdIngestService ingestService;

    public IsyatirimMetalBackfillResponse run(
            String symbolsCsv,
            LocalDate from,
            LocalDate to,
            boolean force
    ) {
        Instant started = Instant.now();
        LocalDate toDate = to != null ? to : LocalDate.now(IST);
        LocalDate fromDate = from != null ? from : toDate.minusYears(properties.getDefaultLookbackYears());
        if (toDate.isBefore(fromDate)) {
            throw new InvalidRequestException("from > to");
        }
        Set<String> symbols = ingestService.resolveBackfillSymbols(symbolsCsv);
        if (symbols.isEmpty()) {
            throw new InvalidRequestException("Geçerli sembol bulunamadı.");
        }
        List<String> requested = new ArrayList<>(symbols);
        List<String> ok = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        long inserted = 0;
        long skippedDup = 0;
        long ignoredRange = 0;
        for (String sym : symbols) {
            PreciousMetalUsdCatalog.Entry entry = PreciousMetalUsdCatalog.byCanonicalOrNull(sym);
            if (entry == null) {
                failed.add(sym);
                continue;
            }
            try {
                IsYatirimMetalUsdIngestService.IngestSummary s =
                        ingestService.ingestRange(entry, fromDate, toDate, force);
                inserted += s.inserted();
                skippedDup += s.skippedDuplicate();
                ignoredRange += s.ignoredOutOfRange();
                ok.add(sym);
            } catch (Exception e) {
                log.warn("ISYATIRIM_METAL_BACKFILL_SYMBOL_FAILED symbol={} error={}", sym, e.getMessage());
                failed.add(sym);
            }
        }
        Instant finished = Instant.now();
        log.info(
                "ISYATIRIM_METAL_BACKFILL_COMPLETED event=ISYATIRIM_METAL_BACKFILL_COMPLETED requested={} ok={} failed={} inserted={} skippedDup={} ignoredRange={}",
                requested.size(),
                ok.size(),
                failed.size(),
                inserted,
                skippedDup,
                ignoredRange);
        return new IsyatirimMetalBackfillResponse(
                requested,
                ok,
                failed,
                inserted,
                skippedDup,
                ignoredRange,
                started,
                finished,
                PreciousMetalUsdCatalog.SOURCE);
    }
}
