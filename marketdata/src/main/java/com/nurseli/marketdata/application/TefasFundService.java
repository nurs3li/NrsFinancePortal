package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.TefasFundHistoryPointDto;
import com.nurseli.marketdata.api.dto.TefasFundPageDto;
import com.nurseli.marketdata.api.dto.TefasFundRowDto;
import com.nurseli.marketdata.config.TefasProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.domain.tefas.TefasFundProfile;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import com.nurseli.marketdata.repository.TefasFundProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TefasFundService {

    private static final int MAX_PAGE_SIZE = 50;

    private final TefasProperties properties;
    private final TefasFundProfileRepository profileRepository;
    private final MarketPriceHistoryRepository priceRepository;

    public TefasFundPageDto list(int page, int size, String sort, String dir, String search) {
        List<TefasFundRowDto> all = loadConfiguredFunds();
        String q = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";
        if (!q.isEmpty()) {
            all = all.stream()
                    .filter(f ->
                            f.code().toLowerCase(Locale.ROOT).contains(q)
                                    || (f.title() != null && f.title().toLowerCase(Locale.ROOT).contains(q))
                                    || (f.fundType() != null && f.fundType().toLowerCase(Locale.ROOT).contains(q)))
                    .toList();
        }
        Comparator<TefasFundRowDto> cmp = comparatorFor(sort, dir);
        all = all.stream().sorted(cmp).toList();

        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        long total = all.size();
        int from = (int) Math.min(total, (long) safePage * safeSize);
        int to = (int) Math.min(total, from + safeSize);
        List<TefasFundRowDto> slice = from >= to ? List.of() : all.subList(from, to);
        int totalPages = total <= 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        return new TefasFundPageDto(
                slice,
                safePage,
                safeSize,
                total,
                totalPages,
                safePage + 1 < totalPages,
                safePage > 0);
    }

    public List<TefasFundHistoryPointDto> history(String code, int periodMonths) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        String symbol = code.trim().toUpperCase(Locale.ROOT);
        if (!isConfiguredSymbol(symbol)) {
            return List.of();
        }
        LocalDate end = LocalDate.now().plusDays(1);
        LocalDate start = end.minusMonths(Math.max(1, Math.min(periodMonths, 60)));
        List<MarketPriceHistory> rows = priceRepository.findBySymbolAndSourceAndTimestampRange(
                symbol,
                TefasFundIngestService.SOURCE_TEFAS,
                start.atStartOfDay(),
                end.atStartOfDay());
        List<TefasFundHistoryPointDto> out = new ArrayList<>();
        for (MarketPriceHistory row : rows) {
            if (row.getTimestamp() == null) {
                continue;
            }
            double px = mid(row);
            if (px <= 0) {
                continue;
            }
            out.add(new TefasFundHistoryPointDto(row.getTimestamp().toLocalDate(), px));
        }
        out.sort(Comparator.comparing(TefasFundHistoryPointDto::date));
        return out;
    }

    public Double latestPrice(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String symbol = code.trim().toUpperCase(Locale.ROOT);
        return priceRepository
                .findTopBySymbolAndSourceOrderByTimestampDesc(symbol, TefasFundIngestService.SOURCE_TEFAS)
                .map(this::mid)
                .filter(v -> v > 0)
                .orElse(null);
    }

    public boolean isConfiguredSymbol(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String symbol = code.trim().toUpperCase(Locale.ROOT);
        return properties.normalizedSymbols().contains(symbol);
    }

    private List<TefasFundRowDto> loadConfiguredFunds() {
        List<String> symbols = properties.normalizedSymbols();
        if (symbols.isEmpty()) {
            return List.of();
        }
        Map<String, TefasFundProfile> profiles = profileRepository.findByCodeIn(symbols).stream()
                .collect(Collectors.toMap(TefasFundProfile::getCode, Function.identity(), (a, b) -> a));
        List<TefasFundRowDto> out = new ArrayList<>(symbols.size());
        for (String code : symbols) {
            TefasFundProfile profile = profiles.get(code);
            Double price = latestPrice(code);
            out.add(new TefasFundRowDto(
                    code,
                    profile != null ? profile.getTitle() : code,
                    profile != null ? profile.getFundType() : null,
                    profile != null ? profile.getRiskLevel() : null,
                    profile == null || profile.getTefasListed() == null || profile.getTefasListed(),
                    price,
                    profile != null ? profile.getReturn1m() : null,
                    profile != null ? profile.getReturn3m() : null,
                    profile != null ? profile.getReturn6m() : null,
                    profile != null ? profile.getReturn1y() : null,
                    profile != null ? profile.getReturnYtd() : null,
                    profile != null ? profile.getReturn3y() : null,
                    profile != null ? profile.getReturn5y() : null,
                    TefasFundIngestService.SOURCE_TEFAS));
        }
        return out;
    }

    private double mid(MarketPriceHistory row) {
        BigDecimal buy = row.getBuyPrice();
        BigDecimal sell = row.getSellPrice();
        if (buy != null && sell != null) {
            return buy.add(sell).divide(BigDecimal.valueOf(2), 8, java.math.RoundingMode.HALF_UP).doubleValue();
        }
        if (buy != null) {
            return buy.doubleValue();
        }
        if (sell != null) {
            return sell.doubleValue();
        }
        return 0;
    }

    private static Comparator<TefasFundRowDto> comparatorFor(String sort, String dir) {
        boolean asc = "asc".equalsIgnoreCase(dir);
        String key = sort != null ? sort.trim().toLowerCase(Locale.ROOT) : "return1y";
        Comparator<TefasFundRowDto> base =
                switch (key) {
                    case "code", "symbol" -> Comparator.comparing(TefasFundRowDto::code, String.CASE_INSENSITIVE_ORDER);
                    case "title", "name", "displayname" ->
                            Comparator.comparing(f -> nullSafe(f.title()), String.CASE_INSENSITIVE_ORDER);
                    case "fundtype", "type" ->
                            Comparator.comparing(f -> nullSafe(f.fundType()), String.CASE_INSENSITIVE_ORDER);
                    case "risk", "risklevel" -> Comparator.comparing(f -> f.riskLevel() != null ? f.riskLevel() : -1);
                    case "price" -> Comparator.comparing(f -> f.price() != null ? f.price() : 0.0);
                    case "return1m", "pctmonth", "getiri1a" -> Comparator.comparing(f -> nz(f.return1m()));
                    case "return3m", "getiri3a" -> Comparator.comparing(f -> nz(f.return3m()));
                    case "return6m", "getiri6a" -> Comparator.comparing(f -> nz(f.return6m()));
                    case "returnytd", "getiriyb" -> Comparator.comparing(f -> nz(f.returnYtd()));
                    case "return3y", "getiri3y" -> Comparator.comparing(f -> nz(f.return3y()));
                    case "return5y", "getiri5y" -> Comparator.comparing(f -> nz(f.return5y()));
                    default -> Comparator.comparing(f -> nz(f.return1y()));
                };
        return asc ? base : base.reversed();
    }

    private static double nz(Double v) {
        return v != null && Double.isFinite(v) ? v : Double.NEGATIVE_INFINITY;
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
