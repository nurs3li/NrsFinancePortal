package com.nurseli.marketdata.application.bist;

import com.nurseli.marketdata.api.dto.BistBackfillItemResponse;
import com.nurseli.marketdata.api.dto.BistBackfillRequest;
import com.nurseli.marketdata.api.dto.BistBackfillResponse;
import com.nurseli.marketdata.api.dto.BistBackfillStatus;
import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class BistEquityBackfillService {

    private static final String PROVIDER_IS_YATIRIM = "IS_YATIRIM";

    private final BistEquityIngestService bistEquityIngestService;
    private final BistSymbolCatalog bistSymbolCatalog;
    private final BistProperties bistProperties;

    public BistBackfillResponse runBackfill(BistBackfillRequest request) {
        String provider = normalizeProvider(request != null ? request.getProvider() : null);
        LocalDate to =
                request != null && request.getTo() != null
                        ? request.getTo()
                        : LocalDate.now(BistEquityDailyConstants.IST);
        LocalDate from =
                request != null && request.getFrom() != null
                        ? request.getFrom()
                        : to.minusYears(Math.max(1, bistProperties.getDefaultLookbackYears()));

        List<String> symbols = resolveSymbols(request != null ? request.getSymbols() : null);

        if (to.isBefore(from)) {
            throw new InvalidRequestException("from, to geçersiz: to < from");
        }

        log.info(
                "[BIST_BACKFILL] started symbolsCount={} from={} to={} provider={}",
                symbols.size(),
                from,
                to,
                provider);

        List<BistBackfillItemResponse> items = new ArrayList<>();
        if (!bistProperties.isEnabled()) {
            for (String sym : symbols) {
                items.add(
                        new BistBackfillItemResponse(
                                sym,
                                BistBackfillStatus.DISABLED,
                                0,
                                0,
                                0,
                                List.of("market.bist.enabled=false")));
                log.warn("[BIST_BACKFILL] disabled symbol={}", sym);
            }
            return summarize(from, to, provider, items);
        }

        for (String sym : symbols) {
            if (!bistSymbolCatalog.isSupported(sym)) {
                items.add(
                        new BistBackfillItemResponse(
                                sym,
                                BistBackfillStatus.SKIPPED,
                                0,
                                0,
                                0,
                                List.of("unsupported_symbol")));
                log.warn("[BIST_BACKFILL] skipped unsupported symbol={}", sym);
                continue;
            }
            BistEquityIngestSymbolResult r;
            try {
                r = bistEquityIngestService.ingestHistory(sym, from, to);
            } catch (Exception ex) {
                log.warn("[BIST_BACKFILL] failed symbol={} reason={}", sym, ex.getMessage());
                items.add(
                        new BistBackfillItemResponse(
                                sym,
                                BistBackfillStatus.FAILED,
                                0,
                                0,
                                0,
                                List.of("exception:" + ex.getClass().getSimpleName())));
                continue;
            }
            BistBackfillStatus status = mapItemStatus(r);
            items.add(
                    new BistBackfillItemResponse(
                            sym,
                            status,
                            r.rowsFetched(),
                            r.rowsWritten(),
                            r.rowsSkipped(),
                            r.warnings()));
            log.info(
                    "[BIST_BACKFILL] symbol={} status={} fetched={} written={} skipped={}",
                    sym,
                    status,
                    r.rowsFetched(),
                    r.rowsWritten(),
                    r.rowsSkipped());
        }

        BistBackfillResponse out = summarize(from, to, provider, items);
        log.info(
                "[BIST_BACKFILL] completed requested={} successful={} failed={} rowsFetched={} rowsWritten={} rowsSkipped={}",
                out.requestedSymbols(),
                out.successfulSymbols(),
                out.failedSymbols(),
                out.totalRowsFetched(),
                out.totalRowsWritten(),
                out.totalRowsSkipped());
        return out;
    }

    private static BistBackfillStatus mapItemStatus(BistEquityIngestSymbolResult r) {
        if (!r.success()) {
            return BistBackfillStatus.FAILED;
        }
        if (r.warnings() != null && !r.warnings().isEmpty()) {
            return BistBackfillStatus.PARTIAL;
        }
        return BistBackfillStatus.SUCCESS;
    }

    private BistBackfillResponse summarize(
            LocalDate from, LocalDate to, String provider, List<BistBackfillItemResponse> items) {
        int ok = 0;
        int fail = 0;
        int tf = 0;
        int tw = 0;
        int ts = 0;
        for (BistBackfillItemResponse it : items) {
            if (it.status() == BistBackfillStatus.SUCCESS || it.status() == BistBackfillStatus.PARTIAL) {
                ok++;
            } else if (it.status() == BistBackfillStatus.FAILED) {
                fail++;
            }
            tf += it.rowsFetched();
            tw += it.rowsWritten();
            ts += it.rowsSkipped();
        }
        return new BistBackfillResponse(
                items.size(), ok, fail, tf, tw, ts, from, to, provider, List.copyOf(items));
    }

    private List<String> resolveSymbols(List<String> requestSymbols) {
        List<String> raw =
                requestSymbols == null || requestSymbols.isEmpty()
                        ? new ArrayList<>(bistProperties.getSymbols())
                        : new ArrayList<>(requestSymbols);
        if (raw.isEmpty()) {
            raw = bistSymbolCatalog.getAll().stream().map(BistSymbolMetadata::symbol).toList();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String c = s.trim().toUpperCase(Locale.ROOT);
            if (!c.isEmpty()) {
                out.add(c);
            }
        }
        return out;
    }

    private static String normalizeProvider(String p) {
        if (p == null || p.isBlank()) {
            return PROVIDER_IS_YATIRIM;
        }
        String u = p.trim().toUpperCase(Locale.ROOT);
        if (!PROVIDER_IS_YATIRIM.equals(u)) {
            throw new InvalidRequestException("Geçersiz provider: " + p + ". Desteklenen: IS_YATIRIM");
        }
        return u;
    }
}
