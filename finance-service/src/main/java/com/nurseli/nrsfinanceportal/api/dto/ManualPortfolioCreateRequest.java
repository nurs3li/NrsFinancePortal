package com.nurseli.nrsfinanceportal.api.dto;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Manuel portföy pozisyonu oluşturma request'i; varlık tipi, sembol, miktar ve alış/satış bilgilerini taşır.
 */
public class ManualPortfolioCreateRequest {

    @NotNull
    private AssetType type;

    @NotBlank
    private String symbol;

    @NotNull
    @DecimalMin(value = "0.00000001")
    private BigDecimal quantity;

    /** Null ise market geçmişinden çözülür. */
    private BigDecimal buyPrice;

    private boolean buyPriceOverride;

    private BigDecimal buyFee;

    @NotNull
    private LocalDate buyDate;

    /** Null ise OPEN kabul edilir. */
    private ManualPositionStatus status;

    private LocalDate sellDate;

    private BigDecimal sellPrice;

    private boolean sellPriceOverride;

    private BigDecimal sellFee;

    private String note;

    public AssetType getType() { return type; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public boolean isBuyPriceOverride() { return buyPriceOverride; }
    public BigDecimal getBuyFee() { return buyFee; }
    public LocalDate getBuyDate() { return buyDate; }
    public ManualPositionStatus getStatus() { return status; }
    public LocalDate getSellDate() { return sellDate; }
    public BigDecimal getSellPrice() { return sellPrice; }
    public boolean isSellPriceOverride() { return sellPriceOverride; }
    public BigDecimal getSellFee() { return sellFee; }
    public String getNote() { return note; }

    public void setType(AssetType type) { this.type = type; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }
    public void setBuyPriceOverride(boolean buyPriceOverride) { this.buyPriceOverride = buyPriceOverride; }
    public void setBuyFee(BigDecimal buyFee) { this.buyFee = buyFee; }
    public void setBuyDate(LocalDate buyDate) { this.buyDate = buyDate; }
    public void setStatus(ManualPositionStatus status) { this.status = status; }
    public void setSellDate(LocalDate sellDate) { this.sellDate = sellDate; }
    public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }
    public void setSellPriceOverride(boolean sellPriceOverride) { this.sellPriceOverride = sellPriceOverride; }
    public void setSellFee(BigDecimal sellFee) { this.sellFee = sellFee; }
    public void setNote(String note) { this.note = note; }
}
