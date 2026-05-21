package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionRealReturnStatus;
import com.nurseli.nrsfinanceportal.service.portfolio.ManualPortfolioRealReturnCalculator;

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

    private final BigDecimal nominalCost;
    private final BigDecimal exitValue;
    private final BigDecimal nominalProfit;
    private final BigDecimal nominalReturnPct;
    private final BigDecimal inflationFactor;
    private final BigDecimal inflationReturnPct;
    private final BigDecimal inflationAdjustedCost;
    private final BigDecimal realProfit;
    private final BigDecimal realReturnPct;
    private final boolean realReturnAvailable;
    private final String realReturnStatus;
    private final LocalDate cpiStartDate;
    private final LocalDate cpiEndDate;
    private final BigDecimal cpiStartValue;
    private final BigDecimal cpiEndValue;
    private final LocalDate calculationEndDate;
    private final String calculationMode;

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
            BigDecimal totalReturnPct,
            BigDecimal nominalCost,
            BigDecimal exitValue,
            BigDecimal nominalProfit,
            BigDecimal nominalReturnPct,
            BigDecimal inflationFactor,
            BigDecimal inflationReturnPct,
            BigDecimal inflationAdjustedCost,
            BigDecimal realProfit,
            BigDecimal realReturnPct,
            boolean realReturnAvailable,
            String realReturnStatus,
            LocalDate cpiStartDate,
            LocalDate cpiEndDate,
            BigDecimal cpiStartValue,
            BigDecimal cpiEndValue,
            LocalDate calculationEndDate,
            String calculationMode
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
        this.nominalCost = nominalCost;
        this.exitValue = exitValue;
        this.nominalProfit = nominalProfit;
        this.nominalReturnPct = nominalReturnPct;
        this.inflationFactor = inflationFactor;
        this.inflationReturnPct = inflationReturnPct;
        this.inflationAdjustedCost = inflationAdjustedCost;
        this.realProfit = realProfit;
        this.realReturnPct = realReturnPct;
        this.realReturnAvailable = realReturnAvailable;
        this.realReturnStatus = realReturnStatus;
        this.cpiStartDate = cpiStartDate;
        this.cpiEndDate = cpiEndDate;
        this.cpiStartValue = cpiStartValue;
        this.cpiEndValue = cpiEndValue;
        this.calculationEndDate = calculationEndDate;
        this.calculationMode = calculationMode;
    }

    public static ManualPortfolioView from(
            ManualPortfolioPosition p,
            ManualPortfolioNominalAnalysis a
    ) {
        return from(p, a, null);
    }

    public static ManualPortfolioView from(
            ManualPortfolioPosition p,
            ManualPortfolioNominalAnalysis a,
            ManualPortfolioRealReturnCalculator.PositionRealReturn real
    ) {
        String statusName = real != null && real.realReturnStatus() != null
                ? real.realReturnStatus().name()
                : ManualPositionRealReturnStatus.NO_CPI_DATA.name();
        String modeName = real != null && real.calculationMode() != null
                ? real.calculationMode().name()
                : null;

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
                a.totalReturnPct(),
                real != null ? real.nominalCost() : null,
                real != null ? real.exitValue() : null,
                real != null ? real.nominalProfit() : null,
                real != null ? real.nominalReturnPct() : null,
                real != null ? real.inflationFactor() : null,
                real != null ? real.inflationReturnPct() : null,
                real != null ? real.inflationAdjustedCost() : null,
                real != null ? real.realProfit() : null,
                real != null ? real.realReturnPct() : null,
                real != null && real.realReturnAvailable(),
                statusName,
                real != null ? real.cpiStartDate() : null,
                real != null ? real.cpiEndDate() : null,
                real != null ? real.cpiStartValue() : null,
                real != null ? real.cpiEndValue() : null,
                real != null ? real.calculationEndDate() : null,
                modeName
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
    public BigDecimal getNominalCost() { return nominalCost; }
    public BigDecimal getExitValue() { return exitValue; }
    public BigDecimal getNominalProfit() { return nominalProfit; }
    public BigDecimal getNominalReturnPct() { return nominalReturnPct; }
    public BigDecimal getInflationFactor() { return inflationFactor; }
    public BigDecimal getInflationReturnPct() { return inflationReturnPct; }
    public BigDecimal getInflationAdjustedCost() { return inflationAdjustedCost; }
    public BigDecimal getRealProfit() { return realProfit; }
    public BigDecimal getRealReturnPct() { return realReturnPct; }
    public boolean isRealReturnAvailable() { return realReturnAvailable; }
    public String getRealReturnStatus() { return realReturnStatus; }
    public LocalDate getCpiStartDate() { return cpiStartDate; }
    public LocalDate getCpiEndDate() { return cpiEndDate; }
    public BigDecimal getCpiStartValue() { return cpiStartValue; }
    public BigDecimal getCpiEndValue() { return cpiEndValue; }
    public LocalDate getCalculationEndDate() { return calculationEndDate; }
    public String getCalculationMode() { return calculationMode; }
}
