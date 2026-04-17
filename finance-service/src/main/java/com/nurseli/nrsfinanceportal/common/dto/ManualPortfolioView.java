package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ManualPortfolioView {

    private final Long id;
    private final String type;
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal buyPrice;
    private final LocalDate buyDate;
    private final String note;

    public ManualPortfolioView(
            Long id,
            String type,
            String symbol,
            BigDecimal quantity,
            BigDecimal buyPrice,
            LocalDate buyDate,
            String note
    ) {
        this.id = id;
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.buyDate = buyDate;
        this.note = note;
    }

    public static ManualPortfolioView from(ManualPortfolioPosition p) {
        return new ManualPortfolioView(
                p.getId(),
                p.getType().name(),
                p.getSymbol(),
                p.getQuantity(),
                p.getBuyPrice(),
                p.getBuyDate(),
                p.getNote()
        );
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public LocalDate getBuyDate() { return buyDate; }
    public String getNote() { return note; }
}