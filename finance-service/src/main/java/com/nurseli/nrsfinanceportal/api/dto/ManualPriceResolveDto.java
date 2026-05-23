package com.nurseli.nrsfinanceportal.api.dto;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Manuel fiyat çözümleme response DTO'su; istenen tarih için bulunan veya bulunamayan fiyat sonucunu taşır.
 * {@link #ok} ve {@link #notFound} factory metodları ile oluşturulur.
 */
public class ManualPriceResolveDto {

    private boolean found;
    private String type;
    private String symbol;
    private LocalDate requestedDate;
    private LocalDate resolvedDate;
    private BigDecimal price;
    private String source;
    private String currency;
    private String message;

    public static ManualPriceResolveDto ok(
            AssetType type,
            String symbol,
            LocalDate requestedDate,
            LocalDate resolvedDate,
            BigDecimal priceTry,
            ManualPriceSource source,
            String currency,
            String message
    ) {
        ManualPriceResolveDto d = new ManualPriceResolveDto();
        d.found = true;
        d.type = type.name();
        d.symbol = symbol;
        d.requestedDate = requestedDate;
        d.resolvedDate = resolvedDate;
        d.price = priceTry;
        d.source = source.name();
        d.currency = currency;
        d.message = message;
        return d;
    }

    public static ManualPriceResolveDto notFound(
            AssetType type,
            String symbol,
            LocalDate requestedDate,
            String message
    ) {
        ManualPriceResolveDto d = new ManualPriceResolveDto();
        d.found = false;
        d.type = type != null ? type.name() : null;
        d.symbol = symbol;
        d.requestedDate = requestedDate;
        d.source = ManualPriceSource.NOT_RESOLVED.name();
        d.message = message;
        return d;
    }

    public boolean isFound() { return found; }
    public String getType() { return type; }
    public String getSymbol() { return symbol; }
    public LocalDate getRequestedDate() { return requestedDate; }
    public LocalDate getResolvedDate() { return resolvedDate; }
    public BigDecimal getPrice() { return price; }
    public String getSource() { return source; }
    public String getCurrency() { return currency; }
    public String getMessage() { return message; }
}
