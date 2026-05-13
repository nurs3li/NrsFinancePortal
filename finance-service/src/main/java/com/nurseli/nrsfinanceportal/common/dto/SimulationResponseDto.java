package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class SimulationResponseDto {

    private final String type;
    private final String symbol;
    private final LocalDate buyDate;
    private final BigDecimal inputAmountTry;
    private final BigDecimal historicalPriceTry;
    private final BigDecimal currentPriceTry;
    private final BigDecimal unitsBought;
    private final BigDecimal currentValueTry;
    private final BigDecimal pnlTry;
    private final BigDecimal pnlPct;
    private final String buyPriceSource; // SYSTEM_HISTORY | USER_INPUT
    private final LocalDate historicalPriceDate;
    private final String qualityFlag; // EXACT | PREVIOUS_DAY | FALLBACK | MISSING
    private final List<SimulationPerformancePointDto> performanceSeries;
    private final String approximationNoticeCode;
    private final String message;

    public SimulationResponseDto(
            String type,
            String symbol,
            LocalDate buyDate,
            BigDecimal inputAmountTry,
            BigDecimal historicalPriceTry,
            BigDecimal currentPriceTry,
            BigDecimal unitsBought,
            BigDecimal currentValueTry,
            BigDecimal pnlTry,
            BigDecimal pnlPct,
            String buyPriceSource,
            LocalDate historicalPriceDate,
            String qualityFlag,
            List<SimulationPerformancePointDto> performanceSeries,
            String approximationNoticeCode,
            String message
    ) {
        this.type = type;
        this.symbol = symbol;
        this.buyDate = buyDate;
        this.inputAmountTry = inputAmountTry;
        this.historicalPriceTry = historicalPriceTry;
        this.currentPriceTry = currentPriceTry;
        this.unitsBought = unitsBought;
        this.currentValueTry = currentValueTry;
        this.pnlTry = pnlTry;
        this.pnlPct = pnlPct;
        this.buyPriceSource = buyPriceSource;
        this.historicalPriceDate = historicalPriceDate;
        this.qualityFlag = qualityFlag;
        this.performanceSeries = performanceSeries;
        this.approximationNoticeCode = approximationNoticeCode;
        this.message = message;
    }

    public String getType() { return type; }
    public String getSymbol() { return symbol; }
    public LocalDate getBuyDate() { return buyDate; }
    public BigDecimal getInputAmountTry() { return inputAmountTry; }
    public BigDecimal getHistoricalPriceTry() { return historicalPriceTry; }
    public BigDecimal getCurrentPriceTry() { return currentPriceTry; }
    public BigDecimal getUnitsBought() { return unitsBought; }
    public BigDecimal getCurrentValueTry() { return currentValueTry; }
    public BigDecimal getPnlTry() { return pnlTry; }
    public BigDecimal getPnlPct() { return pnlPct; }
    public String getBuyPriceSource() { return buyPriceSource; }
    public LocalDate getHistoricalPriceDate() { return historicalPriceDate; }
    public String getQualityFlag() { return qualityFlag; }
    public List<SimulationPerformancePointDto> getPerformanceSeries() { return performanceSeries; }
    public String getApproximationNoticeCode() { return approximationNoticeCode; }
    public String getMessage() { return message; }
}