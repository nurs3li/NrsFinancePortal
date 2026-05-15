package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ManualPortfolioCloseRequest {

    @NotNull
    private LocalDate sellDate;

    private BigDecimal sellPrice;

    private boolean sellPriceOverride;

    private BigDecimal sellFee;

    public LocalDate getSellDate() { return sellDate; }
    public void setSellDate(LocalDate sellDate) { this.sellDate = sellDate; }
    public BigDecimal getSellPrice() { return sellPrice; }
    public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }
    public boolean isSellPriceOverride() { return sellPriceOverride; }
    public void setSellPriceOverride(boolean sellPriceOverride) { this.sellPriceOverride = sellPriceOverride; }
    public BigDecimal getSellFee() { return sellFee; }
    public void setSellFee(BigDecimal sellFee) { this.sellFee = sellFee; }
}
