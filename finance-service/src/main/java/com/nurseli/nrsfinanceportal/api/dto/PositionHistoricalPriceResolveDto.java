package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pozisyon geçmiş fiyat çözümleme response DTO'su; eşleşen tarih, fiyat ve eşleşme türünü taşır.
 * {@link #found} ve {@link #notFound} factory metodları ile oluşturulur.
 */
public class PositionHistoricalPriceResolveDto {

    private String symbol;
    private LocalDate requestedDate;
    private LocalDate matchedDate;
    private BigDecimal price;
    private String source;
    private HistoricalPriceMatchType matchType;
    private String message;

    public static PositionHistoricalPriceResolveDto found(
            String symbol,
            LocalDate requestedDate,
            LocalDate matchedDate,
            BigDecimal price,
            String source,
            HistoricalPriceMatchType matchType,
            String message
    ) {
        PositionHistoricalPriceResolveDto d = new PositionHistoricalPriceResolveDto();
        d.symbol = symbol;
        d.requestedDate = requestedDate;
        d.matchedDate = matchedDate;
        d.price = price;
        d.source = source != null ? source : "market-data-service";
        d.matchType = matchType;
        d.message = message;
        return d;
    }

    public static PositionHistoricalPriceResolveDto notFound(String symbol, LocalDate requestedDate, String message) {
        PositionHistoricalPriceResolveDto d = new PositionHistoricalPriceResolveDto();
        d.symbol = symbol;
        d.requestedDate = requestedDate;
        d.matchType = HistoricalPriceMatchType.NOT_FOUND;
        d.source = "market-data-service";
        d.message = message != null ? message : "Bu tarih için fiyat bulunamadı, manuel giriş yapabilirsiniz.";
        return d;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public LocalDate getMatchedDate() {
        return matchedDate;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getSource() {
        return source;
    }

    public HistoricalPriceMatchType getMatchType() {
        return matchType;
    }

    public String getMessage() {
        return message;
    }
}
