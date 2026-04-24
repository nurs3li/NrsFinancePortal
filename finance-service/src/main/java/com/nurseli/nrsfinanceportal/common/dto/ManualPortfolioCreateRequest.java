package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ManualPortfolioCreateRequest {

    @NotNull
    private AssetType type;

    @NotBlank
    private String symbol;

    @NotNull
    @DecimalMin(value = "0.00000001")
    private BigDecimal quantity;

    @NotNull
    @DecimalMin(value = "0.00000001")
    private BigDecimal buyPrice;

    @NotNull
    private LocalDate buyDate;

    private String note;

    public AssetType getType() { return type; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public LocalDate getBuyDate() { return buyDate; }
    public String getNote() { return note; }

    public void setType(AssetType type) { this.type = type; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }
    public void setBuyDate(LocalDate buyDate) { this.buyDate = buyDate; }
    public void setNote(String note) { this.note = note; }
}