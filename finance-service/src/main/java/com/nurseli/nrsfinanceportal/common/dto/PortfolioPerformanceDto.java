package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.util.List;

public class PortfolioPerformanceDto {

    private final BigDecimal totalCost;
    private final BigDecimal totalCurrentValue;
    private final BigDecimal totalPnl;
    private final BigDecimal totalPnlPct;
    private final List<PerformanceItemDto> items;

    public PortfolioPerformanceDto(
            BigDecimal totalCost,
            BigDecimal totalCurrentValue,
            BigDecimal totalPnl,
            BigDecimal totalPnlPct,
            List<PerformanceItemDto> items
    ) {
        this.totalCost = totalCost;
        this.totalCurrentValue = totalCurrentValue;
        this.totalPnl = totalPnl;
        this.totalPnlPct = totalPnlPct;
        this.items = items;
    }

    public BigDecimal getTotalCost() { return totalCost; }
    public BigDecimal getTotalCurrentValue() { return totalCurrentValue; }
    public BigDecimal getTotalPnl() { return totalPnl; }
    public BigDecimal getTotalPnlPct() { return totalPnlPct; }
    public List<PerformanceItemDto> getItems() { return items; }
}