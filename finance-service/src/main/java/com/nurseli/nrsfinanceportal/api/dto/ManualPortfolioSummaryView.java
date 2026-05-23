package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;

/**
 * Manuel portföy özet görünümü; pozisyon sayıları, yatırım tutarları ve en iyi/kaçırılan fırsat metriklerini taşır.
 */
public class ManualPortfolioSummaryView {

    private final int totalPositions;
    private final int openPositions;
    private final int soldPositions;
    private final BigDecimal totalInvested;
    private final BigDecimal currentOpenValue;
    private final BigDecimal realizedProfit;
    private final BigDecimal unrealizedProfit;
    private final BigDecimal holdValueTodayForSold;
    private final BigDecimal missedProfit;
    private final BigDecimal totalNominalProfit;
    private final BigDecimal totalNominalReturnPct;
    private final String bestPositionSymbol;
    private final BigDecimal bestPositionReturnPct;
    private final String biggestMissedOpportunitySymbol;
    private final BigDecimal biggestMissedProfit;

    public ManualPortfolioSummaryView(
            int totalPositions,
            int openPositions,
            int soldPositions,
            BigDecimal totalInvested,
            BigDecimal currentOpenValue,
            BigDecimal realizedProfit,
            BigDecimal unrealizedProfit,
            BigDecimal holdValueTodayForSold,
            BigDecimal missedProfit,
            BigDecimal totalNominalProfit,
            BigDecimal totalNominalReturnPct,
            String bestPositionSymbol,
            BigDecimal bestPositionReturnPct,
            String biggestMissedOpportunitySymbol,
            BigDecimal biggestMissedProfit
    ) {
        this.totalPositions = totalPositions;
        this.openPositions = openPositions;
        this.soldPositions = soldPositions;
        this.totalInvested = totalInvested;
        this.currentOpenValue = currentOpenValue;
        this.realizedProfit = realizedProfit;
        this.unrealizedProfit = unrealizedProfit;
        this.holdValueTodayForSold = holdValueTodayForSold;
        this.missedProfit = missedProfit;
        this.totalNominalProfit = totalNominalProfit;
        this.totalNominalReturnPct = totalNominalReturnPct;
        this.bestPositionSymbol = bestPositionSymbol;
        this.bestPositionReturnPct = bestPositionReturnPct;
        this.biggestMissedOpportunitySymbol = biggestMissedOpportunitySymbol;
        this.biggestMissedProfit = biggestMissedProfit;
    }

    public int getTotalPositions() { return totalPositions; }
    public int getOpenPositions() { return openPositions; }
    public int getSoldPositions() { return soldPositions; }
    public BigDecimal getTotalInvested() { return totalInvested; }
    public BigDecimal getCurrentOpenValue() { return currentOpenValue; }
    public BigDecimal getRealizedProfit() { return realizedProfit; }
    public BigDecimal getUnrealizedProfit() { return unrealizedProfit; }
    public BigDecimal getHoldValueTodayForSold() { return holdValueTodayForSold; }
    public BigDecimal getMissedProfit() { return missedProfit; }
    public BigDecimal getTotalNominalProfit() { return totalNominalProfit; }
    public BigDecimal getTotalNominalReturnPct() { return totalNominalReturnPct; }
    public String getBestPositionSymbol() { return bestPositionSymbol; }
    public BigDecimal getBestPositionReturnPct() { return bestPositionReturnPct; }
    public String getBiggestMissedOpportunitySymbol() { return biggestMissedOpportunitySymbol; }
    public BigDecimal getBiggestMissedProfit() { return biggestMissedProfit; }
}
