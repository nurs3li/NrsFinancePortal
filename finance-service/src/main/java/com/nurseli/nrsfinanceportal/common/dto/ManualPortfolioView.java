package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ManualPortfolioView {

    private final Long id;
    private final String type;
    private final String symbol;
    private final String displayName;
    private final BigDecimal quantity;
    private final String status;
    private final BigDecimal buyPrice;
    private final LocalDate buyDate;
    private final BigDecimal buyFee;
    private final String buyPriceSource;
    private final LocalDate buyPriceResolvedDate;
    private final boolean buyPriceOverride;
    private final LocalDate sellDate;
    private final BigDecimal sellPrice;
    private final BigDecimal sellFee;
    private final String sellPriceSource;
    private final LocalDate sellPriceResolvedDate;
    private final boolean sellPriceOverride;
    private final String note;

    private final BigDecimal buyCost;
    private final BigDecimal currentPrice;
    private final BigDecimal currentValue;
    private final BigDecimal unrealizedProfit;
    private final BigDecimal unrealizedReturnPct;
    private final BigDecimal sellProceeds;
    private final BigDecimal realizedProfit;
    private final BigDecimal realizedReturnPct;
    private final BigDecimal holdValueToday;
    private final BigDecimal holdProfitToday;
    private final BigDecimal holdReturnPctToday;
    private final BigDecimal missedProfit;
    private final BigDecimal missedReturnPct;
    private final BigDecimal totalProfit;
    private final BigDecimal totalReturnPct;

    public ManualPortfolioView(
            Long id,
            String type,
            String symbol,
            String displayName,
            BigDecimal quantity,
            String status,
            BigDecimal buyPrice,
            LocalDate buyDate,
            BigDecimal buyFee,
            String buyPriceSource,
            LocalDate buyPriceResolvedDate,
            boolean buyPriceOverride,
            LocalDate sellDate,
            BigDecimal sellPrice,
            BigDecimal sellFee,
            String sellPriceSource,
            LocalDate sellPriceResolvedDate,
            boolean sellPriceOverride,
            String note,
            BigDecimal buyCost,
            BigDecimal currentPrice,
            BigDecimal currentValue,
            BigDecimal unrealizedProfit,
            BigDecimal unrealizedReturnPct,
            BigDecimal sellProceeds,
            BigDecimal realizedProfit,
            BigDecimal realizedReturnPct,
            BigDecimal holdValueToday,
            BigDecimal holdProfitToday,
            BigDecimal holdReturnPctToday,
            BigDecimal missedProfit,
            BigDecimal missedReturnPct,
            BigDecimal totalProfit,
            BigDecimal totalReturnPct
    ) {
        this.id = id;
        this.type = type;
        this.symbol = symbol;
        this.displayName = displayName;
        this.quantity = quantity;
        this.status = status;
        this.buyPrice = buyPrice;
        this.buyDate = buyDate;
        this.buyFee = buyFee;
        this.buyPriceSource = buyPriceSource;
        this.buyPriceResolvedDate = buyPriceResolvedDate;
        this.buyPriceOverride = buyPriceOverride;
        this.sellDate = sellDate;
        this.sellPrice = sellPrice;
        this.sellFee = sellFee;
        this.sellPriceSource = sellPriceSource;
        this.sellPriceResolvedDate = sellPriceResolvedDate;
        this.sellPriceOverride = sellPriceOverride;
        this.note = note;
        this.buyCost = buyCost;
        this.currentPrice = currentPrice;
        this.currentValue = currentValue;
        this.unrealizedProfit = unrealizedProfit;
        this.unrealizedReturnPct = unrealizedReturnPct;
        this.sellProceeds = sellProceeds;
        this.realizedProfit = realizedProfit;
        this.realizedReturnPct = realizedReturnPct;
        this.holdValueToday = holdValueToday;
        this.holdProfitToday = holdProfitToday;
        this.holdReturnPctToday = holdReturnPctToday;
        this.missedProfit = missedProfit;
        this.missedReturnPct = missedReturnPct;
        this.totalProfit = totalProfit;
        this.totalReturnPct = totalReturnPct;
    }

    public static ManualPortfolioView from(ManualPortfolioPosition p, ManualPortfolioNominalAnalysis a) {
        return new ManualPortfolioView(
                p.getId(),
                p.getType().name(),
                p.getSymbol(),
                null,
                p.getQuantity(),
                p.getStatus().name(),
                p.getBuyPrice(),
                p.getBuyDate(),
                p.getBuyFee(),
                p.getBuyPriceSource().name(),
                p.getBuyPriceResolvedDate(),
                p.isBuyPriceOverride(),
                p.getSellDate(),
                p.getSellPrice(),
                p.getSellFee(),
                p.getSellPriceSource() != null ? p.getSellPriceSource().name() : null,
                p.getSellPriceResolvedDate(),
                p.isSellPriceOverride(),
                p.getNote(),
                a.buyCost(),
                a.currentPrice(),
                a.currentValue(),
                a.unrealizedProfit(),
                a.unrealizedReturnPct(),
                a.sellProceeds(),
                a.realizedProfit(),
                a.realizedReturnPct(),
                a.holdValueToday(),
                a.holdProfitToday(),
                a.holdReturnPctToday(),
                a.missedProfit(),
                a.missedReturnPct(),
                a.totalProfit(),
                a.totalReturnPct()
        );
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getSymbol() { return symbol; }
    public String getDisplayName() { return displayName; }
    public BigDecimal getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public LocalDate getBuyDate() { return buyDate; }
    public BigDecimal getBuyFee() { return buyFee; }
    public String getBuyPriceSource() { return buyPriceSource; }
    public LocalDate getBuyPriceResolvedDate() { return buyPriceResolvedDate; }
    public boolean isBuyPriceOverride() { return buyPriceOverride; }
    public LocalDate getSellDate() { return sellDate; }
    public BigDecimal getSellPrice() { return sellPrice; }
    public BigDecimal getSellFee() { return sellFee; }
    public String getSellPriceSource() { return sellPriceSource; }
    public LocalDate getSellPriceResolvedDate() { return sellPriceResolvedDate; }
    public boolean isSellPriceOverride() { return sellPriceOverride; }
    public String getNote() { return note; }
    public BigDecimal getBuyCost() { return buyCost; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public BigDecimal getUnrealizedProfit() { return unrealizedProfit; }
    public BigDecimal getUnrealizedReturnPct() { return unrealizedReturnPct; }
    public BigDecimal getSellProceeds() { return sellProceeds; }
    public BigDecimal getRealizedProfit() { return realizedProfit; }
    public BigDecimal getRealizedReturnPct() { return realizedReturnPct; }
    public BigDecimal getHoldValueToday() { return holdValueToday; }
    public BigDecimal getHoldProfitToday() { return holdProfitToday; }
    public BigDecimal getHoldReturnPctToday() { return holdReturnPctToday; }
    public BigDecimal getMissedProfit() { return missedProfit; }
    public BigDecimal getMissedReturnPct() { return missedReturnPct; }
    public BigDecimal getTotalProfit() { return totalProfit; }
    public BigDecimal getTotalReturnPct() { return totalReturnPct; }
}
