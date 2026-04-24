package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class UnifiedPortfolioItemView {

    private final String source; // TRADE | MANUAL
    private final String type;
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal avgBuyPrice;
    /** MANUAL satirlari icin DB id; TRADE icin null */
    private final Long manualPositionId;
    /** MANUAL satirlari icin alis tarihi */
    private final LocalDate manualBuyDate;
    private final String manualNote;

    public UnifiedPortfolioItemView(
            String source,
            String type,
            String symbol,
            BigDecimal quantity,
            BigDecimal avgBuyPrice,
            Long manualPositionId,
            LocalDate manualBuyDate,
            String manualNote
    ) {
        this.source = source;
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
        this.manualPositionId = manualPositionId;
        this.manualBuyDate = manualBuyDate;
        this.manualNote = manualNote;
    }

    public String getSource() {
        return source;
    }

    public String getType() {
        return type;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAvgBuyPrice() {
        return avgBuyPrice;
    }

    public Long getManualPositionId() {
        return manualPositionId;
    }

    public LocalDate getManualBuyDate() {
        return manualBuyDate;
    }

    public String getManualNote() {
        return manualNote;
    }
}
