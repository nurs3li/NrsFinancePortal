package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "manual_portfolio_positions")
public class ManualPortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AssetType type;

    @Column(name = "symbol", nullable = false, length = 30)
    private String symbol;

    @Column(name = "quantity", nullable = false, precision = 38, scale = 8)
    private BigDecimal quantity;

    @Column(name = "buy_price", nullable = false, precision = 38, scale = 8)
    private BigDecimal buyPrice;

    @Column(name = "buy_date", nullable = false)
    private LocalDate buyDate;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ManualPositionStatus status = ManualPositionStatus.OPEN;

    @Column(name = "buy_fee", precision = 38, scale = 8)
    private BigDecimal buyFee;

    @Column(name = "sell_date")
    private LocalDate sellDate;

    @Column(name = "sell_price", precision = 38, scale = 8)
    private BigDecimal sellPrice;

    @Column(name = "sell_fee", precision = 38, scale = 8)
    private BigDecimal sellFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "buy_price_source", nullable = false, length = 40)
    private ManualPriceSource buyPriceSource = ManualPriceSource.USER_INPUT;

    @Column(name = "buy_price_resolved_date")
    private LocalDate buyPriceResolvedDate;

    @Column(name = "buy_price_override", nullable = false)
    private boolean buyPriceOverride;

    @Enumerated(EnumType.STRING)
    @Column(name = "sell_price_source", length = 40)
    private ManualPriceSource sellPriceSource;

    @Column(name = "sell_price_resolved_date")
    private LocalDate sellPriceResolvedDate;

    @Column(name = "sell_price_override", nullable = false)
    private boolean sellPriceOverride;

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

    protected ManualPortfolioPosition() {
    }

    public static ManualPortfolioPosition createNew(
            User user,
            AssetType type,
            String symbol,
            BigDecimal quantity,
            LocalDate buyDate,
            BigDecimal buyPrice,
            ManualPriceSource buyPriceSource,
            LocalDate buyPriceResolvedDate,
            boolean buyPriceOverride,
            BigDecimal buyFee,
            ManualPositionStatus status,
            LocalDate sellDate,
            BigDecimal sellPrice,
            ManualPriceSource sellPriceSource,
            LocalDate sellPriceResolvedDate,
            boolean sellPriceOverride,
            BigDecimal sellFee,
            String note
    ) {
        ManualPortfolioPosition p = new ManualPortfolioPosition();
        p.user = user;
        p.type = type;
        p.symbol = symbol;
        p.quantity = quantity;
        p.buyDate = buyDate;
        p.buyPrice = buyPrice;
        p.buyPriceSource = buyPriceSource;
        p.buyPriceResolvedDate = buyPriceResolvedDate;
        p.buyPriceOverride = buyPriceOverride;
        p.buyFee = buyFee;
        p.status = status != null ? status : ManualPositionStatus.OPEN;
        p.sellDate = sellDate;
        p.sellPrice = sellPrice;
        p.sellPriceSource = sellPriceSource;
        p.sellPriceResolvedDate = sellPriceResolvedDate;
        p.sellPriceOverride = sellPriceOverride;
        p.sellFee = sellFee;
        p.note = note;
        return p;
    }

    public void applyFullUpdate(
            AssetType type,
            String symbol,
            BigDecimal quantity,
            LocalDate buyDate,
            BigDecimal buyPrice,
            ManualPriceSource buyPriceSource,
            LocalDate buyPriceResolvedDate,
            boolean buyPriceOverride,
            BigDecimal buyFee,
            ManualPositionStatus status,
            LocalDate sellDate,
            BigDecimal sellPrice,
            ManualPriceSource sellPriceSource,
            LocalDate sellPriceResolvedDate,
            boolean sellPriceOverride,
            BigDecimal sellFee,
            String note
    ) {
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
        this.buyDate = buyDate;
        this.buyPrice = buyPrice;
        this.buyPriceSource = buyPriceSource;
        this.buyPriceResolvedDate = buyPriceResolvedDate;
        this.buyPriceOverride = buyPriceOverride;
        this.buyFee = buyFee;
        this.status = status != null ? status : ManualPositionStatus.OPEN;
        this.sellDate = sellDate;
        this.sellPrice = sellPrice;
        this.sellPriceSource = sellPriceSource;
        this.sellPriceResolvedDate = sellPriceResolvedDate;
        this.sellPriceOverride = sellPriceOverride;
        this.sellFee = sellFee;
        this.note = note;
    }

    public void closeAsSold(
            LocalDate sellDate,
            BigDecimal sellPrice,
            ManualPriceSource sellPriceSource,
            LocalDate sellPriceResolvedDate,
            boolean sellPriceOverride,
            BigDecimal sellFee
    ) {
        this.status = ManualPositionStatus.SOLD;
        this.sellDate = sellDate;
        this.sellPrice = sellPrice;
        this.sellPriceSource = sellPriceSource;
        this.sellPriceResolvedDate = sellPriceResolvedDate;
        this.sellPriceOverride = sellPriceOverride;
        this.sellFee = sellFee;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public AssetType getType() { return type; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public LocalDate getBuyDate() { return buyDate; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public ManualPositionStatus getStatus() { return status; }
    public BigDecimal getBuyFee() { return buyFee; }
    public LocalDate getSellDate() { return sellDate; }
    public BigDecimal getSellPrice() { return sellPrice; }
    public BigDecimal getSellFee() { return sellFee; }
    public ManualPriceSource getBuyPriceSource() { return buyPriceSource; }
    public LocalDate getBuyPriceResolvedDate() { return buyPriceResolvedDate; }
    public boolean isBuyPriceOverride() { return buyPriceOverride; }
    public ManualPriceSource getSellPriceSource() { return sellPriceSource; }
    public LocalDate getSellPriceResolvedDate() { return sellPriceResolvedDate; }
    public boolean isSellPriceOverride() { return sellPriceOverride; }
}
