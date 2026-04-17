package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    public String getMessage() { return message; }
}