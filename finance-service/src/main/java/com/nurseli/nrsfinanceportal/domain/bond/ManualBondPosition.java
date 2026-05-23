package com.nurseli.nrsfinanceportal.domain.bond;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Manuel tahvil/eurobond pozisyonu entity; nominal, kupon, vade, alış/satış
 * ve realize PnL alanlarını içerir.
 */
@Entity
@Table(name = "manual_bond_positions")
public class ManualBondPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "symbol", nullable = false, length = 80)
    private String symbol;

    @Column(name = "display_name", length = 180)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "bond_type", nullable = false, length = 40)
    private BondType bondType;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "nominal_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal nominalValue;

    @Column(name = "buy_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal buyPrice;

    @Column(name = "buy_date", nullable = false)
    private LocalDate buyDate;

    @Column(name = "current_price", precision = 19, scale = 6)
    private BigDecimal currentPrice;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "coupon_rate", precision = 10, scale = 4)
    private BigDecimal couponRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_frequency", length = 30)
    private CouponFrequency couponFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BondPositionStatus status = BondPositionStatus.OPEN;

    @Column(name = "sell_price", precision = 19, scale = 6)
    private BigDecimal sellPrice;

    @Column(name = "sell_date")
    private LocalDate sellDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_type", length = 20)
    private BondCloseType closeType;

    @Column(name = "close_fee", precision = 19, scale = 6)
    private BigDecimal closeFee;

    @Column(name = "collected_coupon_amount", precision = 19, scale = 6)
    private BigDecimal collectedCouponAmount;

    @Column(name = "realized_pnl", precision = 19, scale = 6)
    private BigDecimal realizedPnl;

    @Column(name = "realized_return_percent", precision = 10, scale = 4)
    private BigDecimal realizedReturnPercent;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    protected ManualBondPosition() {
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getSymbol() { return symbol; }
    public String getDisplayName() { return displayName; }
    public BondType getBondType() { return bondType; }
    public String getCurrency() { return currency; }
    public BigDecimal getNominalValue() { return nominalValue; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public LocalDate getBuyDate() { return buyDate; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public BigDecimal getCouponRate() { return couponRate; }
    public CouponFrequency getCouponFrequency() { return couponFrequency; }
    public BondPositionStatus getStatus() { return status; }
    public BigDecimal getSellPrice() { return sellPrice; }
    public LocalDate getSellDate() { return sellDate; }
    public BondCloseType getCloseType() { return closeType; }
    public BigDecimal getCloseFee() { return closeFee; }
    public BigDecimal getCollectedCouponAmount() { return collectedCouponAmount; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public BigDecimal getRealizedReturnPercent() { return realizedReturnPercent; }
    public String getNote() { return note; }

    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setBondType(BondType bondType) { this.bondType = bondType; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setNominalValue(BigDecimal nominalValue) { this.nominalValue = nominalValue; }
    public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }
    public void setBuyDate(LocalDate buyDate) { this.buyDate = buyDate; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public void setCouponRate(BigDecimal couponRate) { this.couponRate = couponRate; }
    public void setCouponFrequency(CouponFrequency couponFrequency) { this.couponFrequency = couponFrequency; }
    public void setStatus(BondPositionStatus status) { this.status = status; }
    public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }
    public void setSellDate(LocalDate sellDate) { this.sellDate = sellDate; }
    public void setCloseType(BondCloseType closeType) { this.closeType = closeType; }
    public void setCloseFee(BigDecimal closeFee) { this.closeFee = closeFee; }
    public void setCollectedCouponAmount(BigDecimal collectedCouponAmount) { this.collectedCouponAmount = collectedCouponAmount; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public void setRealizedReturnPercent(BigDecimal realizedReturnPercent) { this.realizedReturnPercent = realizedReturnPercent; }
    public void setNote(String note) { this.note = note; }

    public static ManualBondPosition createNew(
            User user,
            String symbol,
            String displayName,
            BondType bondType,
            String currency,
            BigDecimal nominalValue,
            BigDecimal buyPrice,
            LocalDate buyDate,
            BigDecimal currentPrice,
            LocalDate maturityDate,
            BigDecimal couponRate,
            CouponFrequency couponFrequency,
            String note
    ) {
        ManualBondPosition p = new ManualBondPosition();
        p.user = user;
        p.symbol = symbol;
        p.displayName = displayName;
        p.bondType = bondType;
        p.currency = currency;
        p.nominalValue = nominalValue;
        p.buyPrice = buyPrice;
        p.buyDate = buyDate;
        p.currentPrice = currentPrice;
        p.maturityDate = maturityDate;
        p.couponRate = couponRate;
        p.couponFrequency = couponFrequency;
        p.status = BondPositionStatus.OPEN;
        p.note = note;
        return p;
    }
}
