package com.nurseli.nrsfinanceportal.service.viopbond;

import com.nurseli.nrsfinanceportal.common.dto.HistoricalPriceMatchType;
import com.nurseli.nrsfinanceportal.common.dto.PositionHistoricalPriceResolveDto;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.DebtHistoryRow;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.ViopPriceAtRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PositionHistoricalPriceResolverService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final String SOURCE = "market-data-service";

    private final MarketDataClient marketDataClient;

    public PositionHistoricalPriceResolveDto resolveViop(String symbol, LocalDate requestedDate) {
        if (symbol == null || symbol.isBlank() || requestedDate == null) {
            return PositionHistoricalPriceResolveDto.notFound(symbol, requestedDate, "Geçersiz parametre.");
        }
        LocalDate today = LocalDate.now(TZ);
        if (requestedDate.isAfter(today)) {
            return PositionHistoricalPriceResolveDto.notFound(symbol, requestedDate, "Gelecek tarih için fiyat çözülemez.");
        }

        String normalized = symbol.trim().toUpperCase();
        String stripped = normalized.startsWith("F_") ? normalized.substring(2) : normalized;
        ViopPriceAtRow row = tryViopPriceAt(stripped, requestedDate);
        if (row == null && !normalized.equals(stripped)) {
            row = tryViopPriceAt(normalized, requestedDate);
        }
        if (row == null && !stripped.isBlank()) {
            row = tryViopPriceAt("F_" + stripped, requestedDate);
        }
        if (row == null) {
            return PositionHistoricalPriceResolveDto.notFound(
                    normalized,
                    requestedDate,
                    "Bu tarih için VİOP fiyatı bulunamadı, manuel giriş yapabilirsiniz.");
        }

        return mapViopRow(normalized, requestedDate, row);
    }

    public PositionHistoricalPriceResolveDto resolveBond(String symbol, LocalDate requestedDate) {
        if (symbol == null || symbol.isBlank() || requestedDate == null) {
            return PositionHistoricalPriceResolveDto.notFound(symbol, requestedDate, "Geçersiz parametre.");
        }
        String isin = symbol.trim().toUpperCase();
        LocalDate today = LocalDate.now(TZ);
        if (requestedDate.isAfter(today)) {
            return PositionHistoricalPriceResolveDto.notFound(isin, requestedDate, "Gelecek tarih için fiyat çözülemez.");
        }

        long spanDays = ChronoUnit.DAYS.between(requestedDate, today) + 14;
        int days = (int) Math.min(400, Math.max(30, spanDays));
        List<DebtHistoryRow> rows = marketDataClient.getDebtHistory(isin, days);
        NavigableMap<LocalDate, BigDecimal> byDay = aggregateDebtByDay(rows);
        if (byDay.isEmpty()) {
            return PositionHistoricalPriceResolveDto.notFound(
                    isin,
                    requestedDate,
                    "Bu tarih için fiyat bulunamadı, manuel giriş yapabilirsiniz.");
        }

        BigDecimal exact = byDay.get(requestedDate);
        if (exact != null) {
            return PositionHistoricalPriceResolveDto.found(
                    isin,
                    requestedDate,
                    requestedDate,
                    exact,
                    SOURCE,
                    HistoricalPriceMatchType.EXACT,
                    "Tarihsel fiyatla otomatik dolduruldu.");
        }

        Map.Entry<LocalDate, BigDecimal> prev = byDay.lowerEntry(requestedDate);
        if (prev != null && prev.getValue() != null && prev.getValue().signum() > 0) {
            return PositionHistoricalPriceResolveDto.found(
                    isin,
                    requestedDate,
                    prev.getKey(),
                    prev.getValue(),
                    SOURCE,
                    HistoricalPriceMatchType.PREVIOUS_CLOSE,
                    "Seçilen tarihte veri yok; en yakın önceki işlem günü fiyatı kullanıldı.");
        }

        Map.Entry<LocalDate, BigDecimal> next = byDay.higherEntry(requestedDate);
        if (next != null && next.getValue() != null && next.getValue().signum() > 0) {
            return PositionHistoricalPriceResolveDto.found(
                    isin,
                    requestedDate,
                    next.getKey(),
                    next.getValue(),
                    SOURCE,
                    HistoricalPriceMatchType.NEXT_AVAILABLE,
                    "Seçilen tarihte veri yok; en yakın sonraki işlem günü fiyatı kullanıldı.");
        }

        return PositionHistoricalPriceResolveDto.notFound(
                isin,
                requestedDate,
                "Bu tarih için fiyat bulunamadı, manuel giriş yapabilirsiniz.");
    }

    private ViopPriceAtRow tryViopPriceAt(String contractCode, LocalDate requestedDate) {
        try {
            return marketDataClient.getViopPriceAt(contractCode, requestedDate).orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private PositionHistoricalPriceResolveDto mapViopRow(String symbol, LocalDate requestedDate, ViopPriceAtRow row) {
        if (row == null || row.matchType() == null) {
            return PositionHistoricalPriceResolveDto.notFound(
                    symbol,
                    requestedDate,
                    "Bu tarih için VİOP fiyatı bulunamadı, manuel giriş yapabilirsiniz.");
        }
        String mt = row.matchType().trim().toUpperCase();
        if ("NOT_FOUND".equals(mt) || row.price() == null || row.price().signum() <= 0) {
            return PositionHistoricalPriceResolveDto.notFound(
                    symbol,
                    requestedDate,
                    "Bu tarih için VİOP fiyatı bulunamadı, manuel giriş yapabilirsiniz.");
        }

        LocalDate matchedDate = row.matchedPriceTime() != null
                ? row.matchedPriceTime().atZone(TZ).toLocalDate()
                : requestedDate;

        HistoricalPriceMatchType matchType = switch (mt) {
            case "EXACT" -> HistoricalPriceMatchType.EXACT;
            case "PREVIOUS_AVAILABLE", "PREVIOUS_CLOSE" -> HistoricalPriceMatchType.PREVIOUS_CLOSE;
            case "NEXT_AVAILABLE" -> HistoricalPriceMatchType.NEXT_AVAILABLE;
            default -> HistoricalPriceMatchType.PREVIOUS_CLOSE;
        };

        String message = matchType == HistoricalPriceMatchType.EXACT
                ? "Tarihsel fiyatla otomatik dolduruldu."
                : "Seçilen tarihte veri yok; en yakın önceki işlem günü fiyatı kullanıldı.";

        String source = row.source() != null && !row.source().isBlank() ? row.source() : SOURCE;
        return PositionHistoricalPriceResolveDto.found(
                symbol,
                requestedDate,
                matchedDate,
                row.price(),
                source,
                matchType,
                message);
    }

    private static NavigableMap<LocalDate, BigDecimal> aggregateDebtByDay(List<DebtHistoryRow> rows) {
        TreeMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (rows == null) {
            return map;
        }
        rows.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DebtHistoryRow::asOf, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(row -> {
                    if (row.asOf() == null || row.dirtyPrice() == null || row.dirtyPrice().signum() <= 0) {
                        return;
                    }
                    LocalDate d = row.asOf().atZone(TZ).toLocalDate();
                    map.put(d, row.dirtyPrice());
                });
        return map;
    }
}
