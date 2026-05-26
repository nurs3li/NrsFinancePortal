package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Yatırım simülasyonu response DTO'su; geçmiş alım senaryosu, güncel değer, PnL ve performans serisini taşır.
 */
public class SimulationResponseDto {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_PREPARING = "PREPARING";

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
    /** TRY veya USD — parasal alanlar bu birimde. */
    private final String displayCurrency;
    private final String status;
    private final Integer retryAfterSeconds;

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
            String message,
            String displayCurrency
    ) {
        this(
                type,
                symbol,
                buyDate,
                inputAmountTry,
                historicalPriceTry,
                currentPriceTry,
                unitsBought,
                currentValueTry,
                pnlTry,
                pnlPct,
                buyPriceSource,
                historicalPriceDate,
                qualityFlag,
                performanceSeries,
                approximationNoticeCode,
                message,
                displayCurrency,
                STATUS_READY,
                null
        );
    }

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
            String message,
            String displayCurrency,
            String status,
            Integer retryAfterSeconds
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
        this.displayCurrency = displayCurrency;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static SimulationResponseDto preparing(
            String type,
            String symbol,
            LocalDate buyDate,
            BigDecimal inputAmount,
            String displayCurrency,
            String message,
            Integer retryAfterSeconds
    ) {
        return new SimulationResponseDto(
                type,
                symbol,
                buyDate,
                inputAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "SYSTEM_HISTORY",
                null,
                "MISSING",
                List.of(),
                "SIMULATION_HISTORY_PREPARING",
                message,
                displayCurrency,
                STATUS_PREPARING,
                retryAfterSeconds
        );
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
    public String getDisplayCurrency() { return displayCurrency; }
    public String getStatus() { return status; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
}