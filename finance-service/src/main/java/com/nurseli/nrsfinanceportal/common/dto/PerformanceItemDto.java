package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

public class PerformanceItemDto {

    private final String source;
    private final String type;
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal avgBuyPrice;
    private final BigDecimal currentPrice;
    private final String currentPriceCurrency;
    private final BigDecimal cost;
    private final BigDecimal currentValue;
    private final BigDecimal pnl;
    private final BigDecimal pnlPct;
    /** MANUAL satirlari icin; TRADE icin null */
    private final Long manualPositionId;

    public PerformanceItemDto(
            String source,
            String type,
            String symbol,
            BigDecimal quantity,
            BigDecimal avgBuyPrice,
            BigDecimal currentPrice,
            String currentPriceCurrency,
            BigDecimal cost,
            BigDecimal currentValue,
            BigDecimal pnl,
            BigDecimal pnlPct,
            Long manualPositionId
    ) {
        this.source = source;
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
        this.currentPrice = currentPrice;
        this.currentPriceCurrency = currentPriceCurrency;
        this.cost = cost;
        this.currentValue = currentValue;
        this.pnl = pnl;
        this.pnlPct = pnlPct;
        this.manualPositionId = manualPositionId;
    }

    public String getSource() { return source; }
    public String getType() { return type; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAvgBuyPrice() { return avgBuyPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public String getCurrentPriceCurrency() { return currentPriceCurrency; }
    public BigDecimal getCost() { return cost; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public BigDecimal getPnl() { return pnl; }
    public BigDecimal getPnlPct() { return pnlPct; }

    public Long getManualPositionId() {
        return manualPositionId;
    }
}