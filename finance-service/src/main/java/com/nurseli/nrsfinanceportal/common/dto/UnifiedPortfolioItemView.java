package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

public class UnifiedPortfolioItemView {

    private final String source; // TRADE | MANUAL
    private final String type;
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal avgBuyPrice;

    public UnifiedPortfolioItemView(
            String source,
            String type,
            String symbol,
            BigDecimal quantity,
            BigDecimal avgBuyPrice
    ) {
        this.source = source;
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
    }

    public String getSource() { return source; }
    public String getType() { return type; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAvgBuyPrice() { return avgBuyPrice; }
}