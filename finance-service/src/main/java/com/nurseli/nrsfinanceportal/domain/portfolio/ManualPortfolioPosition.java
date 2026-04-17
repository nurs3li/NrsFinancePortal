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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ManualPortfolioPosition() {
    }

    public static ManualPortfolioPosition create(
            User user,
            AssetType type,
            String symbol,
            BigDecimal quantity,
            BigDecimal buyPrice,
            LocalDate buyDate,
            String note
    ) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (buyPrice == null || buyPrice.signum() <= 0) {
            throw new IllegalArgumentException("Buy price must be positive");
        }
        if (buyDate == null) {
            throw new IllegalArgumentException("Buy date is required");
        }

        ManualPortfolioPosition p = new ManualPortfolioPosition();
        p.user = user;
        p.type = type;
        p.symbol = symbol;
        p.quantity = quantity;
        p.buyPrice = buyPrice;
        p.buyDate = buyDate;
        p.note = note;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(
            BigDecimal quantity,
            BigDecimal buyPrice,
            LocalDate buyDate,
            String note
    ) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (buyPrice == null || buyPrice.signum() <= 0) {
            throw new IllegalArgumentException("Buy price must be positive");
        }
        if (buyDate == null) {
            throw new IllegalArgumentException("Buy date is required");
        }

        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.buyDate = buyDate;
        this.note = note;
        this.updatedAt = Instant.now();
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
}