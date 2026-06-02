package com.nurseli.nrsfinanceportal.application.market;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.api.dto.MarketTerminalListItemDto;
import com.nurseli.nrsfinanceportal.api.dto.MarketTerminalListPageResponse;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceLatestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * finance-service market terminal liste servisi — BIST, TEFAS, VIOP, bond ve spot kategoriler için sayfalı terminal listesi üretir.
 */
@RequiredArgsConstructor
@Service

public class MarketTerminalListService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int HISTORY_DAYS = 400;
    /** Liste spark / horizon — tam yıl için ~252 gün ideal; 90 gün terminal gecikmesini düşürür. */
    private static final int BOND_HISTORY_DAYS = 90;
    private static final List<String> PRECIOUS_METAL_SYMBOLS =
            List.of("XAU_TRY", "XAU_USD_OZ", "XAG_USD_OZ", "XPT_USD_OZ", "XPD_USD_OZ");

    private static final Map<String, String> VIOP_CATEGORY = Map.ofEntries(
            Map.entry("F_XU0301226", "INDEX"),
            Map.entry("F_XLBNK1226", "INDEX"),
            Map.entry("F_USDTRY1226", "FX"),
            Map.entry("F_EURTRY1226", "FX"),
            Map.entry("F_XAUTRYM1026", "COMMODITY"),
            Map.entry("F_XAUUSD1026", "COMMODITY"),
            Map.entry("F_GARAN0726", "EQUITY"),
            Map.entry("F_THYAO0726", "EQUITY"),
            Map.entry("F_ASELS0726", "EQUITY"),
            Map.entry("F_AKBNK0726", "EQUITY"),
            Map.entry("F_SISE0726", "EQUITY"),
            Map.entry("F_EREGL0726", "EQUITY"));

    private final MarketDataClient marketDataClient;

    /**
     * {@code list} — Kategori ve alt pazar parametrelerine göre ilgili listeleme akışını yönlendirir (BIST, TEFAS, VIOP, bond veya spot).
     */
    public MarketTerminalListPageResponse list(
            String category,
            String equitySubmarket,
            String fundSubmarket,
            int page,
            int size,
            String filter,
            String sort,
            String dir,
            String search) {
        String cat = category != null ? category.trim().toUpperCase(Locale.ROOT) : "";
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        String f = filter != null ? filter.trim().toUpperCase(Locale.ROOT) : "ALL";
        String q = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";

        if ("FUNDS".equals(cat) && "TR".equalsIgnoreCase(fundSubmarket)) {
            return listTefas(safePage, safeSize, sort, dir, q);
        }
        if ("EQUITY".equals(cat) && "BIST".equalsIgnoreCase(equitySubmarket)) {
            return listBist(safePage, safeSize, f, sort, dir, q);
        }
        if ("FUTURES".equals(cat)) {
            return listViop(safePage, safeSize, f, sort, dir, q);
        }
        if ("BOND".equals(cat)) {
            return listBond(safePage, safeSize, f, sort, dir, q);
        }
        return listSpotCategory(cat, safePage, safeSize, f, sort, dir, q);
    }

    private MarketTerminalListPageResponse listBist(
            int page, int size, String filter, String sort, String dir, String search) {
        MarketDataClient.BistLatestPage paged =
                marketDataClient.getBistLatestPage(page, size, mapSort(sort), dir, filter, blankToNull(search));
        List<MarketDataClient.BistLatestRow> rows = paged.content() != null ? paged.content() : List.of();
        if (rows.isEmpty()) {
            return toPageResponse(List.of(), page, size, paged.totalElements());
        }
        String symbolsCsv = rows.stream()
                .map(MarketDataClient.BistLatestRow::symbol)
                .filter(Objects::nonNull)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(HISTORY_DAYS);
        Map<String, List<MarketPriceHistoryDto>> hist =
                marketDataClient.getBistBatchHistoryMapped(symbolsCsv, from, to);
        List<MarketTerminalListItemDto> items = new ArrayList<>(rows.size());
        for (var r : rows) {
            String sym = norm(r.symbol());
            List<Double> spark = closes(hist.get(sym));
            double px = bistPrice(r);
            double pct = bd(r.changePercent());
            Horizon hz = horizonsFromCloses(spark, pct);
            items.add(new MarketTerminalListItemDto(
                    sym,
                    "EQUITY",
                    "BIST",
                    null,
                    trim(r.displayName()),
                    trim(r.displayName()),
                    px,
                    pct,
                    pct,
                    pct >= 0 ? "UP" : "DOWN",
                    bdObj(r.volume()),
                    "TRY",
                    "TR",
                    "BIST",
                    trim(r.sector()),
                    trim(r.source()),
                    null,
                    spark,
                    hz.pctDay(),
                    hz.pctWeek(),
                    hz.pctMonth(),
                    hz.pctYear(),
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
                    null));
        }
        return toPageResponse(items, paged.page(), paged.size(), paged.totalElements());
    }

    private MarketTerminalListPageResponse listTefas(int page, int size, String sort, String dir, String search) {
        MarketDataClient.TefasFundPage paged = marketDataClient.getTefasFundPage(page, size, sort, dir, search);
        List<MarketDataClient.TefasFundRow> rows = paged.items() != null ? paged.items() : List.of();
        List<MarketTerminalListItemDto> items = new ArrayList<>(rows.size());
        for (MarketDataClient.TefasFundRow r : rows) {
            double rYtd =
                    r.returnYtd() != null && Double.isFinite(r.returnYtd()) ? r.returnYtd() : 0.0;
            String trend = rYtd >= 0 ? "UP" : "DOWN";
            double px = r.price() != null && r.price() > 0 ? r.price() : 0.0;
            items.add(new MarketTerminalListItemDto(
                    r.code(),
                    "FUNDS",
                    null,
                    "TR",
                    r.title(),
                    r.title(),
                    px,
                    rYtd,
                    r.return1m(),
                    trend,
                    null,
                    "TRY",
                    "TR",
                    "TEFAS",
                    r.fundType(),
                    "TEFAS",
                    null,
                    List.of(),
                    r.return1m(),
                    r.return3m(),
                    r.return6m(),
                    r.return1y(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    r.riskLevel(),
                    r.return3m(),
                    r.return6m(),
                    r.return3y(),
                    r.return5y()));
        }
        return toPageResponse(items, paged.page(), paged.size(), paged.totalElements());
    }

    private MarketTerminalListPageResponse listViop(
            int page, int size, String filter, String sort, String dir, String search) {
        List<MarketDataClient.ViopLatestRow> rows = marketDataClient.getViopLatestRows().stream()
                .filter(r -> r.contractCode() != null && !r.contractCode().isBlank())
                .filter(r -> bd(r.price()) > 0)
                .toList();
        List<MarketDataClient.ViopLatestRow> filtered = applyViopFilter(rows, filter, search);
        Comparator<MarketDataClient.ViopLatestRow> cmp = viopComparator(sort, dir);
        filtered = filtered.stream().sorted(cmp).toList();
        return sliceAndMap(filtered, page, size, this::toViopItem);
    }

    private MarketTerminalListItemDto toViopItem(MarketDataClient.ViopLatestRow r) {
        String sym = norm(r.contractCode());
        List<Double> spark = r.sparklineCloses() != null
                ? r.sparklineCloses().stream().filter(Objects::nonNull).map(BigDecimal::doubleValue).filter(d -> d > 0).toList()
                : List.of();
        double px = bd(r.price());
        double pct =
                firstFinite(bdObj(r.listPctChange14d()), bdObj(r.seqMovePct()), bdObj(r.listPctChange1d()), 0.0);
        Horizon hz = new Horizon(
                bdObj(r.listPctChange1d()),
                bdObj(r.listPctChange7d()),
                bdObj(r.listPctChange30d()),
                bdObj(r.listPctChange365d()));
        if (hz.pctDay() == null) {
            hz = horizonsFromCloses(spark, pct);
        }
        return new MarketTerminalListItemDto(
                sym,
                "FUTURES",
                null,
                null,
                sym,
                sym,
                px,
                pct,
                pct,
                pct >= 0 ? "UP" : "DOWN",
                r.dailyVolume() != null ? r.dailyVolume().doubleValue() : null,
                null,
                null,
                null,
                null,
                null,
                null,
                spark,
                hz.pctDay(),
                hz.pctWeek(),
                hz.pctMonth(),
                hz.pctYear(),
                null,
                null,
                null,
                null,
                null,
                null,
                trim(r.contractMonth()),
                bdObj(r.basis()),
                bdObj(r.marginRequirement()),
                null,
                null,
                null,
                null,
                null);
    }

    private MarketTerminalListPageResponse listBond(
            int page, int size, String filter, String sort, String dir, String search) {
        List<MarketDataClient.DebtLatestRow> merged = marketDataClient.getDebtLatestRows().stream()
                .filter(r -> r.isin() != null && !r.isin().isBlank())
                .filter(r -> !Boolean.TRUE.equals(r.synthetic()))
                .collect(Collectors.toMap(
                        r -> norm(r.isin()),
                        Function.identity(),
                        MarketTerminalListService::mergeDebtLatestRows,
                        LinkedHashMap::new))
                .values()
                .stream()
                .filter(r -> bd(r.dirtyPrice()) > 0)
                .toList();

        List<BondLightCandidate> lights = merged.stream()
                .map(r -> {
                    String sym = norm(r.isin());
                    if (!search.isEmpty() && !sym.toLowerCase(Locale.ROOT).contains(search)) {
                        return null;
                    }
                    double px = bd(r.dirtyPrice());
                    return new BondLightCandidate(r, sym, px);
                })
                .filter(Objects::nonNull)
                .toList();

        List<BondLightCandidate> filtered = applyGenericFilter(
                lights,
                filter,
                search,
                BondLightCandidate::symbol,
                BondLightCandidate::symbol,
                c -> new FilterCtx(c.price(), 0.0, c.volume()));

        Comparator<BondLightCandidate> cmp = bondLightComparator(sort, dir);
        filtered = filtered.stream().sorted(cmp).toList();

        long total = filtered.size();
        int from = Math.min((int) total, page * size);
        int to = Math.min((int) total, from + size);
        List<BondLightCandidate> pageRows = from >= to ? List.of() : filtered.subList(from, to);

        List<MarketTerminalListItemDto> items = pageRows.parallelStream()
                .map(this::enrichBondCandidate)
                .toList();
        return toPageResponse(items, page, size, total);
    }

    private MarketTerminalListItemDto enrichBondCandidate(BondLightCandidate light) {
        MarketDataClient.DebtLatestRow r = light.row();
        String sym = light.symbol();
        double px = light.price();
        List<Double> spark = debtCloses(marketDataClient.getDebtHistory(sym, BOND_HISTORY_DAYS));
        if (spark.isEmpty() && px > 0) {
            spark = List.of(px);
        }
        double pct = pctFromSpark(spark, 0);
        Horizon hz = horizonsFromCloses(spark, pct);
        return new MarketTerminalListItemDto(
                sym,
                "BOND",
                null,
                null,
                sym,
                sym,
                px,
                pct,
                pct,
                pct >= 0 ? "UP" : "DOWN",
                px > 0 ? px * 100 : null,
                null,
                null,
                null,
                null,
                null,
                null,
                spark,
                hz.pctDay(),
                hz.pctWeek(),
                hz.pctMonth(),
                hz.pctYear(),
                trim(r.maturityDate()),
                r.daysToMaturity(),
                bdObj(r.couponRate()),
                null,
                r.couponFrequencyPerYear(),
                trim(r.couponFrequencyLabel()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static List<Double> debtCloses(List<MarketDataClient.DebtHistoryRow> hist) {
        if (hist == null || hist.isEmpty()) {
            return List.of();
        }
        return hist.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        MarketDataClient.DebtHistoryRow::asOf,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(MarketDataClient.DebtHistoryRow::dirtyPrice)
                .map(MarketTerminalListService::bdObj)
                .filter(v -> v != null && v > 0)
                .toList();
    }

    private static Comparator<BondCandidate> bondComparator(String sort, String dir) {
        boolean asc = "asc".equalsIgnoreCase(dir);
        Comparator<BondCandidate> base =
                switch (sort != null ? sort.toLowerCase(Locale.ROOT) : "") {
                    case "price" -> Comparator.comparing(BondCandidate::price);
                    case "pctday" -> Comparator.comparing(
                            c -> c.horizon().pctDay() != null ? c.horizon().pctDay() : c.changePercent());
                    case "pctweek" -> Comparator.comparing(
                            c -> c.horizon().pctWeek() != null ? c.horizon().pctWeek() : 0.0);
                    case "pctmonth" -> Comparator.comparing(
                            c -> c.horizon().pctMonth() != null ? c.horizon().pctMonth() : 0.0);
                    case "pctyear" -> Comparator.comparing(
                            c -> c.horizon().pctYear() != null ? c.horizon().pctYear() : 0.0);
                    case "symbol" -> Comparator.comparing(BondCandidate::symbol, String.CASE_INSENSITIVE_ORDER);
                    default -> Comparator.comparing(c -> Math.abs(c.changePercent()));
                };
        return asc ? base : base.reversed();
    }

    /** Ön sıralama: horizon % yalnızca sayfa satırlarında hesaplanır. */
    private static Comparator<BondLightCandidate> bondLightComparator(String sort, String dir) {
        boolean asc = "asc".equalsIgnoreCase(dir);
        Comparator<BondLightCandidate> base =
                switch (sort != null ? sort.toLowerCase(Locale.ROOT) : "") {
                    case "price" -> Comparator.comparing(BondLightCandidate::price);
                    case "pctday", "pctweek", "pctmonth", "pctyear" -> Comparator.comparing(BondLightCandidate::price);
                    case "symbol" -> Comparator.comparing(BondLightCandidate::symbol, String.CASE_INSENSITIVE_ORDER);
                    default -> Comparator.comparing(BondLightCandidate::symbol, String.CASE_INSENSITIVE_ORDER);
                };
        return asc ? base : base.reversed();
    }

    private MarketTerminalListPageResponse listSpotCategory(
            String category, int page, int size, String filter, String sort, String dir, String search) {
        MarketDataClient.LatestPricingSnapshot snap = marketDataClient.loadLatestPricing();
        Map<String, MarketPriceLatestDto> latest = latestForCategory(snap, category);
        List<Map.Entry<String, MarketPriceLatestDto>> entries = new ArrayList<>(latest.entrySet());
        if ("METALS".equals(category)) {
            entries = PRECIOUS_METAL_SYMBOLS.stream()
                    .map(sym -> Map.entry(sym, latest.get(sym)))
                    .filter(e -> e.getValue() != null)
                    .toList();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (var e : entries) {
            String sym = norm(e.getKey());
            MarketPriceLatestDto row = e.getValue();
            double px = midPrice(row);
            if (!(px > 0)) continue;
            candidates.add(new Candidate(sym, px, 0, List.of(), null));
        }
        AssetType type = assetTypeForCategory(category);
        boolean needsPct =
                !"ALL".equals(filter)
                        || (sort != null && sort.toLowerCase(Locale.ROOT).startsWith("pct"));
        if (needsPct) {
            List<Candidate> enriched = new ArrayList<>(candidates.size());
            for (Candidate c : candidates) {
                List<MarketPriceHistoryDto> hist = marketDataClient.getHistory(type, c.symbol(), 5);
                List<Double> spark = closesFromHistory(hist);
                double pct = pctFromSpark(spark, 0);
                enriched.add(new Candidate(c.symbol(), c.price(), pct, spark, c.volume()));
            }
            candidates = enriched;
        }
        List<Candidate> filtered = applyGenericFilter(
                candidates,
                filter,
                search,
                Candidate::symbol,
                Candidate::symbol,
                c -> new FilterCtx(c.price(), c.changePercent(), c.volume()));
        Comparator<Candidate> cmp = candidateComparator(sort, dir);
        if ("VOL".equals(filter)) {
            filtered = filtered.stream()
                    .sorted(Comparator.comparing(
                            (Candidate c) -> c.volume() != null ? c.volume() : 0.0, Comparator.reverseOrder()))
                    .toList();
        } else {
            filtered = filtered.stream().sorted(cmp).toList();
        }
        long total = filtered.size();
        int from = Math.min((int) total, page * size);
        int to = Math.min((int) total, from + size);
        List<Candidate> pageRows = from >= to ? List.of() : filtered.subList(from, to);
        List<MarketTerminalListItemDto> items = new ArrayList<>(pageRows.size());
        for (Candidate c : pageRows) {
            List<MarketPriceHistoryDto> hist = marketDataClient.getHistory(type, c.symbol(), 90);
            List<Double> spark = closesFromHistory(hist);
            double pct = pctFromSpark(spark, 0);
            Horizon hz = horizonsFromCloses(spark, pct);
            items.add(new MarketTerminalListItemDto(
                    c.symbol(),
                    category,
                    "EQUITY".equals(category) ? "US" : null,
                    "FUNDS".equals(category) ? "US" : null,
                    c.symbol(),
                    c.symbol(),
                    c.price(),
                    pct,
                    pct,
                    pct >= 0 ? "UP" : "DOWN",
                    c.volume(),
                    "EQUITY".equals(category) || "FUNDS".equals(category) ? "USD" : null,
                    "EQUITY".equals(category) ? "US" : null,
                    null,
                    null,
                    rowSource(latest.get(c.symbol())),
                    null,
                    spark,
                    hz.pctDay(),
                    hz.pctWeek(),
                    hz.pctMonth(),
                    hz.pctYear(),
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
                    null));
        }
        return toPageResponse(items, page, size, total);
    }

    private static String rowSource(MarketPriceLatestDto row) {
        return row != null && row.source() != null ? row.source().trim() : null;
    }

    private static Map<String, MarketPriceLatestDto> latestForCategory(
            MarketDataClient.LatestPricingSnapshot snap, String category) {
        return switch (category) {
            case "FX" -> snap.fx();
            case "CRYPTO" -> snap.crypto();
            case "METALS" -> snap.metals();
            case "FUNDS" -> snap.funds();
            case "EQUITY" -> snap.equity();
            default -> Map.of();
        };
    }

    private static AssetType assetTypeForCategory(String category) {
        return switch (category) {
            case "FX" -> AssetType.FX;
            case "CRYPTO" -> AssetType.CRYPTO;
            case "METALS" -> AssetType.METAL;
            case "FUNDS" -> AssetType.FUND;
            case "EQUITY" -> AssetType.STOCK;
            default -> AssetType.STOCK;
        };
    }

    private static List<MarketDataClient.ViopLatestRow> applyViopFilter(
            List<MarketDataClient.ViopLatestRow> rows, String filter, String search) {
        String wantCat =
                switch (filter) {
                    case "VIOP_FX" -> "FX";
                    case "VIOP_INDEX" -> "INDEX";
                    case "VIOP_COMMODITY" -> "COMMODITY";
                    case "VIOP_EQUITY" -> "EQUITY";
                    default -> null;
                };
        return rows.stream()
                .filter(r -> {
                    if (!search.isEmpty()) {
                        String sym = norm(r.contractCode()).toLowerCase(Locale.ROOT);
                        if (!sym.contains(search)) return false;
                    }
                    if (wantCat == null) return true;
                    String cat = viopCategory(norm(r.contractCode()));
                    return wantCat.equals(cat);
                })
                .toList();
    }

    private static String viopCategory(String code) {
        if (code == null) return null;
        String c = code.toUpperCase(Locale.ROOT);
        if (VIOP_CATEGORY.containsKey(c)) return VIOP_CATEGORY.get(c);
        if (!c.startsWith("F_") && VIOP_CATEGORY.containsKey("F_" + c)) return VIOP_CATEGORY.get("F_" + c);
        return null;
    }

    private <T> MarketTerminalListPageResponse sliceAndMap(
            List<T> rows, int page, int size, Function<T, MarketTerminalListItemDto> mapper) {
        long total = rows.size();
        int from = Math.min((int) total, page * size);
        int to = Math.min((int) total, from + size);
        List<MarketTerminalListItemDto> items =
                rows.subList(from, to).stream().map(mapper).toList();
        return toPageResponse(items, page, size, total);
    }

    private static MarketTerminalListPageResponse toPageResponse(
            List<MarketTerminalListItemDto> items, int page, int size, long totalElements) {
        int safeSize = Math.max(1, size);
        int totalPages = totalElements <= 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int safePage = Math.max(0, page);
        return new MarketTerminalListPageResponse(
                items, safePage, safeSize, totalElements, totalPages, safePage + 1 < totalPages, safePage > 0);
    }

    private static MarketDataClient.DebtLatestRow mergeDebtLatestRows(
            MarketDataClient.DebtLatestRow a,
            MarketDataClient.DebtLatestRow b) {
        MarketDataClient.DebtLatestRow newer = debtRowIsNewer(a, b) ? a : b;
        MarketDataClient.DebtLatestRow older = newer == a ? b : a;
        java.math.BigDecimal price = bd(newer.dirtyPrice()) > 0 ? newer.dirtyPrice() : older.dirtyPrice();
        java.math.BigDecimal coupon = debtCouponRate(newer) != null ? debtCouponRate(newer) : debtCouponRate(older);
        java.time.LocalDateTime asOf = newer.asOf() != null ? newer.asOf() : older.asOf();
        return new MarketDataClient.DebtLatestRow(
                newer.isin() != null ? newer.isin() : older.isin(),
                price,
                newer.yieldPct(),
                coupon,
                newer.maturityDate() != null ? newer.maturityDate() : older.maturityDate(),
                newer.daysToMaturity() != null ? newer.daysToMaturity() : older.daysToMaturity(),
                newer.synthetic(),
                asOf,
                newer.couponFrequencyPerYear() != null ? newer.couponFrequencyPerYear() : older.couponFrequencyPerYear(),
                newer.couponFrequencyLabel() != null ? newer.couponFrequencyLabel() : older.couponFrequencyLabel(),
                newer.couponFrequencySource() != null ? newer.couponFrequencySource() : older.couponFrequencySource());
    }

    private static boolean debtRowIsNewer(MarketDataClient.DebtLatestRow a, MarketDataClient.DebtLatestRow b) {
        if (a.asOf() == null) return false;
        if (b.asOf() == null) return true;
        return a.asOf().isAfter(b.asOf());
    }

    private static java.math.BigDecimal debtCouponRate(MarketDataClient.DebtLatestRow r) {
        if (r == null || r.couponRate() == null || r.couponRate().signum() <= 0) {
            return null;
        }
        return r.couponRate();
    }

    private record Candidate(String symbol, double price, double changePercent, List<Double> spark, Double volume) {}

    private record BondLightCandidate(MarketDataClient.DebtLatestRow row, String symbol, double price) {
        Double volume() {
            return price > 0 ? price * 100 : null;
        }
    }

    private record BondCandidate(
            MarketDataClient.DebtLatestRow row,
            String symbol,
            double price,
            double changePercent,
            List<Double> spark,
            Horizon horizon) {
        Double volume() {
            return price > 0 ? price * 100 : null;
        }
    }

    private record FilterCtx(double price, double changePercent, Double volume) {}

    private static <T> List<T> applyGenericFilter(
            List<T> rows,
            String filter,
            String search,
            Function<T, String> symbolFn,
            Function<T, String> labelFn,
            Function<T, FilterCtx> ctxFn) {
        return rows.stream()
                .filter(r -> {
                    FilterCtx ctx = ctxFn.apply(r);
                    String sym = symbolFn.apply(r).toLowerCase(Locale.ROOT);
                    String label = labelFn.apply(r).toLowerCase(Locale.ROOT);
                    if (!search.isEmpty() && !sym.contains(search) && !label.contains(search)) {
                        return false;
                    }
                    return switch (filter) {
                        case "UP" -> ctx.changePercent() > 0.02;
                        case "DOWN" -> ctx.changePercent() < -0.02;
                        case "VOL" -> ctx.volume() != null && ctx.volume() > 0;
                        default -> true;
                    };
                })
                .toList();
    }

    private static Comparator<MarketDataClient.ViopLatestRow> viopComparator(String sort, String dir) {
        boolean asc = "asc".equalsIgnoreCase(dir);
        Comparator<MarketDataClient.ViopLatestRow> base =
                switch (sort != null ? sort.toLowerCase(Locale.ROOT) : "") {
                    case "price" -> Comparator.comparing(r -> bd(r.price()));
                    case "pctday" -> Comparator.comparing(r -> bd(r.listPctChange1d()));
                    case "pctweek" -> Comparator.comparing(r -> bd(r.listPctChange7d()));
                    case "pctmonth" -> Comparator.comparing(r -> bd(r.listPctChange30d()));
                    case "pctyear" -> Comparator.comparing(r -> bd(r.listPctChange365d()));
                    case "symbol" -> Comparator.comparing(
                            r -> r.contractCode() != null ? r.contractCode() : "", String.CASE_INSENSITIVE_ORDER);
                    default -> Comparator.comparing(
                            r -> Math.abs(firstFinite(bdObj(r.listPctChange14d()), bdObj(r.seqMovePct()), 0.0)));
                };
        return asc ? base : base.reversed();
    }

    private static Comparator<Candidate> candidateComparator(String sort, String dir) {
        boolean asc = "asc".equalsIgnoreCase(dir);
        Comparator<Candidate> base =
                switch (sort != null ? sort.toLowerCase(Locale.ROOT) : "") {
                    case "price" -> Comparator.comparing(Candidate::price);
                    case "symbol" -> Comparator.comparing(Candidate::symbol, String.CASE_INSENSITIVE_ORDER);
                    default -> Comparator.comparing(c -> Math.abs(c.changePercent()));
                };
        return asc ? base : base.reversed();
    }

    private static String mapSort(String sort) {
        if (sort == null || sort.isBlank()) return "changePercent";
        return switch (sort) {
            case "pctDay" -> "changePercent";
            case "pctWeek" -> "changePercent";
            case "pctMonth" -> "changePercent";
            case "pctYear" -> "changePercent";
            default -> sort;
        };
    }

    private static Horizon horizonsFromCloses(List<Double> closes, double fallbackDay) {
        if (closes == null || closes.size() < 2) {
            return new Horizon(fallbackDay, null, null, null);
        }
        int n = closes.size();
        double last = closes.get(n - 1);
        return new Horizon(
                pctAtSpan(closes, last, 1, fallbackDay),
                pctAtSpan(closes, last, 7, null),
                pctAtSpan(closes, last, 30, null),
                pctAtSpan(closes, last, 252, null));
    }

    private static Double pctAtSpan(List<Double> closes, double last, int span, Double fallback) {
        int n = closes.size();
        int idx = Math.max(0, n - 1 - span);
        double prev = closes.get(idx);
        if (prev <= 0 || last <= 0) return fallback;
        return ((last - prev) / prev) * 100.0;
    }

    private static double pctFromSpark(List<Double> spark, double fallback) {
        if (spark.size() < 2) return fallback;
        double a = spark.get(spark.size() - 2);
        double b = spark.get(spark.size() - 1);
        if (a <= 0 || b <= 0) return fallback;
        return ((b - a) / a) * 100.0;
    }

    private static List<Double> closes(List<MarketPriceHistoryDto> hist) {
        if (hist == null) return List.of();
        return hist.stream()
                .map(MarketTerminalListService::histClose)
                .filter(v -> v != null && v > 0)
                .toList();
    }

    private static List<Double> closesFromHistory(List<MarketPriceHistoryDto> hist) {
        return closes(hist);
    }

    private static Double histClose(MarketPriceHistoryDto h) {
        if (h == null) return null;
        BigDecimal b = h.buyPrice();
        if (b != null && b.signum() > 0) return b.doubleValue();
        BigDecimal s = h.sellPrice();
        return s != null && s.signum() > 0 ? s.doubleValue() : null;
    }

    private static double midPrice(MarketPriceLatestDto row) {
        if (row == null) return 0;
        BigDecimal buy = row.buyPrice();
        BigDecimal sell = row.sellPrice();
        if (buy != null && sell != null && buy.signum() > 0 && sell.signum() > 0) {
            return buy.add(sell).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP).doubleValue();
        }
        if (buy != null && buy.signum() > 0) return buy.doubleValue();
        if (sell != null && sell.signum() > 0) return sell.doubleValue();
        return 0;
    }

    private static double bistPrice(MarketDataClient.BistLatestRow r) {
        if (r.adjustedClose() != null && r.adjustedClose().signum() > 0) {
            return r.adjustedClose().doubleValue();
        }
        if (r.rawClose() != null && r.rawClose().signum() > 0) {
            return r.rawClose().doubleValue();
        }
        return 0;
    }

    private static double bd(BigDecimal v) {
        return v != null ? v.doubleValue() : 0;
    }

    private static Double bdObj(BigDecimal v) {
        return v != null ? v.doubleValue() : null;
    }

    @SafeVarargs
    private static double firstFinite(Double... vals) {
        for (Double v : vals) {
            if (v != null && Double.isFinite(v)) return v;
        }
        return 0;
    }

    private static String norm(String s) {
        return s != null ? s.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static String trim(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    private record Horizon(Double pctDay, Double pctWeek, Double pctMonth, Double pctYear) {}
}
