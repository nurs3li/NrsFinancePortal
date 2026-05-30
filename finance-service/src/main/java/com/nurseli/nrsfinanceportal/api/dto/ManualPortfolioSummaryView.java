package com.nurseli.nrsfinanceportal.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonCreator
    public ManualPortfolioSummaryView(
            @JsonProperty("totalPositions") int totalPositions,
            @JsonProperty("openPositions") int openPositions,
            @JsonProperty("soldPositions") int soldPositions,
            @JsonProperty("totalInvested") BigDecimal totalInvested,
            @JsonProperty("currentOpenValue") BigDecimal currentOpenValue,
            @JsonProperty("realizedProfit") BigDecimal realizedProfit,
            @JsonProperty("unrealizedProfit") BigDecimal unrealizedProfit,
            @JsonProperty("holdValueTodayForSold") BigDecimal holdValueTodayForSold,
            @JsonProperty("missedProfit") BigDecimal missedProfit,
            @JsonProperty("totalNominalProfit") BigDecimal totalNominalProfit,
            @JsonProperty("totalNominalReturnPct") BigDecimal totalNominalReturnPct,
            @JsonProperty("bestPositionSymbol") String bestPositionSymbol,
            @JsonProperty("bestPositionReturnPct") BigDecimal bestPositionReturnPct,
            @JsonProperty("biggestMissedOpportunitySymbol") String biggestMissedOpportunitySymbol,
            @JsonProperty("biggestMissedProfit") BigDecimal biggestMissedProfit
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
