package com.nurseli.marketdata.infrastructure.bist.isyatirim;

import com.nurseli.marketdata.infrastructure.bist.BistDataQuality;
import com.nurseli.marketdata.infrastructure.bist.BistEquityDailyPrice;
import com.nurseli.marketdata.infrastructure.bist.BistParseResult;
import com.nurseli.marketdata.infrastructure.bist.BistProviderSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * İş Yatırım HisseTekil JSON satırlarını {@link BistEquityDailyPrice} listesine dönüştürür.
 * HTTP/WebClient içermez.
 */
@Component
public class IsYatirimHisseTekilParser {

    private static final DateTimeFormatter HGDG_TARIH_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final Clock clock;

    public IsYatirimHisseTekilParser(@Autowired(required = false) Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public BistParseResult parse(List<IsYatirimHisseTekilRow> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) {
            return BistParseResult.empty();
        }
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        List<BistEquityDailyPrice> out = new ArrayList<>();
        for (IsYatirimHisseTekilRow row : rawRows) {
            try {
                BistEquityDailyPrice mapped = tryMapRow(row, warnings);
                if (mapped == null) {
                    skipped++;
                } else {
                    out.add(mapped);
                }
            } catch (Exception ex) {
                skipped++;
                warnings.add("parse row failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        out.sort(Comparator.comparing(BistEquityDailyPrice::date));
        List<String> frozen = warnings.isEmpty() ? List.of() : List.copyOf(warnings);
        boolean allHistorical = !out.isEmpty()
                && out.stream().allMatch(r -> r.dataQuality() == BistDataQuality.HISTORICAL);
        if (allHistorical) {
            return BistParseResult.success(out, skipped, frozen);
        }
        return BistParseResult.partial(out, skipped, frozen);
    }

    private BistEquityDailyPrice tryMapRow(IsYatirimHisseTekilRow row, List<String> warnings) {
        String symRaw = row.hgdgHsKodu();
        if (symRaw == null || symRaw.trim().isEmpty()) {
            warnings.add("skip: missing HGDG_HS_KODU");
            return null;
        }
        String symbol = symRaw.trim().toUpperCase(Locale.ROOT);

        String dateRaw = row.hgdgTarih();
        if (dateRaw == null || dateRaw.isBlank()) {
            warnings.add("skip " + symbol + ": missing HGDG_TARIH");
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dateRaw.trim(), HGDG_TARIH_FMT);
        } catch (DateTimeParseException ex) {
            warnings.add("skip " + symbol + "/" + dateRaw + ": invalid HGDG_TARIH");
            return null;
        }

        BigDecimal adjClose = row.hgdgKapanis();
        if (adjClose == null) {
            warnings.add("skip " + symbol + "/" + date + ": missing HGDG_KAPANIS");
            return null;
        }

        BigDecimal adjAvg = row.hgdgAof();
        BigDecimal adjLow = row.hgdgMin();
        BigDecimal adjHigh = row.hgdgMax();
        BigDecimal adjVol = row.hgdgHacim();

        BigDecimal rawClose = row.hgKapanis();
        BigDecimal rawAvg = row.hgAof();
        BigDecimal rawLow = row.hgMin();
        BigDecimal rawHigh = row.hgMax();
        BigDecimal rawVol = row.hgHacim();

        BigDecimal usdTry = row.ddDeger();
        BigDecimal bist100 = row.endDeger();
        BigDecimal usdPrice = row.dolarBazliFiyat();
        BigDecimal indexBased = row.endeksBazliFiyat();
        BigDecimal usdVol = row.dolarHacim();
        BigDecimal capital = row.sermaye();
        BigDecimal mcapTry = row.pd();
        BigDecimal mcapUsd = row.pdUsd();
        BigDecimal ffTry = row.haoPd();
        BigDecimal ffUsd = row.haoPdUsd();
        BigDecimal dbLow = row.dolarBazliMin();
        BigDecimal dbHigh = row.dolarBazliMax();
        BigDecimal dbAvg = row.dolarBazliAof();

        BistDataQuality quality = classifyQuality(
                adjClose,
                adjAvg,
                adjLow,
                adjHigh,
                adjVol,
                rawClose,
                rawAvg,
                rawLow,
                rawHigh,
                rawVol,
                usdTry,
                bist100,
                usdPrice,
                indexBased,
                usdVol,
                capital,
                mcapTry,
                mcapUsd,
                ffTry,
                ffUsd,
                dbLow,
                dbHigh,
                dbAvg
        );

        Instant lastUpdated = clock.instant();
        return new BistEquityDailyPrice(
                symbol,
                date,
                adjClose,
                adjAvg,
                adjLow,
                adjHigh,
                adjVol,
                rawClose,
                rawAvg,
                rawLow,
                rawHigh,
                rawVol,
                usdTry,
                bist100,
                usdPrice,
                indexBased,
                usdVol,
                capital,
                mcapTry,
                mcapUsd,
                ffTry,
                ffUsd,
                dbLow,
                dbHigh,
                dbAvg,
                BistProviderSource.IS_YATIRIM,
                quality,
                lastUpdated
        );
    }

    /**
     * Tam HGDG + HG + zenginleştirme alanları doluysa {@link BistDataQuality#HISTORICAL}; aksi {@link BistDataQuality#PARTIAL}.
     */
    private static BistDataQuality classifyQuality(
            BigDecimal adjClose,
            BigDecimal adjAvg,
            BigDecimal adjLow,
            BigDecimal adjHigh,
            BigDecimal adjVol,
            BigDecimal rawClose,
            BigDecimal rawAvg,
            BigDecimal rawLow,
            BigDecimal rawHigh,
            BigDecimal rawVol,
            BigDecimal usdTry,
            BigDecimal bist100,
            BigDecimal usdPrice,
            BigDecimal indexBased,
            BigDecimal usdVol,
            BigDecimal capital,
            BigDecimal mcapTry,
            BigDecimal mcapUsd,
            BigDecimal ffTry,
            BigDecimal ffUsd,
            BigDecimal dbLow,
            BigDecimal dbHigh,
            BigDecimal dbAvg) {
        if (adjClose == null) {
            return BistDataQuality.PARTIAL;
        }
        boolean hgdFull =
                nonNull(adjAvg) && nonNull(adjLow) && nonNull(adjHigh) && nonNull(adjVol);
        boolean hgFull =
                nonNull(rawClose)
                        && nonNull(rawAvg)
                        && nonNull(rawLow)
                        && nonNull(rawHigh)
                        && nonNull(rawVol);
        boolean enrichmentFull =
                nonNull(usdTry)
                        && nonNull(bist100)
                        && nonNull(usdPrice)
                        && nonNull(indexBased)
                        && nonNull(usdVol)
                        && nonNull(capital)
                        && nonNull(mcapTry)
                        && nonNull(mcapUsd)
                        && nonNull(ffTry)
                        && nonNull(ffUsd)
                        && nonNull(dbLow)
                        && nonNull(dbHigh)
                        && nonNull(dbAvg);

        if (hgdFull && hgFull && enrichmentFull) {
            return BistDataQuality.HISTORICAL;
        }
        return BistDataQuality.PARTIAL;
    }

    private static boolean nonNull(BigDecimal v) {
        return v != null;
    }
}
