package com.nurseli.nrsfinanceportal.domain.viop;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Manuel VİOP pozisyonu entity; kontrat, yön, giriş/kapanış fiyatları
 * ve realize PnL alanlarını içerir.
 */
@Entity
@Table(name = "manual_viop_positions")
public class ManualViopPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "symbol", nullable = false, length = 60)
    private String symbol;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "viop_category", nullable = false, length = 30)
    private ViopCategory viopCategory;

    @Column(name = "underlying_symbol", length = 60)
    private String underlyingSymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private ViopDirection direction;

    @Column(name = "contract_count", nullable = false, precision = 19, scale = 6)
    private BigDecimal contractCount;

    @Column(name = "entry_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal entryPrice;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "current_price", precision = 19, scale = 6)
    private BigDecimal currentPrice;

    @Column(name = "contract_multiplier", nullable = false, precision = 19, scale = 6)
    private BigDecimal contractMultiplier;

    @Column(name = "initial_margin", precision = 19, scale = 6)
    private BigDecimal initialMargin;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ViopPositionStatus status = ViopPositionStatus.OPEN;

    @Column(name = "close_price", precision = 19, scale = 6)
    private BigDecimal closePrice;

    @Column(name = "close_date")
    private LocalDate closeDate;

    @Column(name = "close_fee", precision = 19, scale = 6)
    private BigDecimal closeFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 30)
    private ViopCloseReason closeReason;

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

    protected ManualViopPosition() {
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getSymbol() { return symbol; }
    public String getDisplayName() { return displayName; }
    public ViopCategory getViopCategory() { return viopCategory; }
    public String getUnderlyingSymbol() { return underlyingSymbol; }
    public ViopDirection getDirection() { return direction; }
    public BigDecimal getContractCount() { return contractCount; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public LocalDate getEntryDate() { return entryDate; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getContractMultiplier() { return contractMultiplier; }
    public BigDecimal getInitialMargin() { return initialMargin; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public ViopPositionStatus getStatus() { return status; }
    public BigDecimal getClosePrice() { return closePrice; }
    public LocalDate getCloseDate() { return closeDate; }
    public BigDecimal getCloseFee() { return closeFee; }
    public ViopCloseReason getCloseReason() { return closeReason; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public BigDecimal getRealizedReturnPercent() { return realizedReturnPercent; }
    public String getNote() { return note; }

    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setViopCategory(ViopCategory viopCategory) { this.viopCategory = viopCategory; }
    public void setUnderlyingSymbol(String underlyingSymbol) { this.underlyingSymbol = underlyingSymbol; }
    public void setDirection(ViopDirection direction) { this.direction = direction; }
    public void setContractCount(BigDecimal contractCount) { this.contractCount = contractCount; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public void setContractMultiplier(BigDecimal contractMultiplier) { this.contractMultiplier = contractMultiplier; }
    public void setInitialMargin(BigDecimal initialMargin) { this.initialMargin = initialMargin; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public void setStatus(ViopPositionStatus status) { this.status = status; }
    public void setClosePrice(BigDecimal closePrice) { this.closePrice = closePrice; }
    public void setCloseDate(LocalDate closeDate) { this.closeDate = closeDate; }
    public void setCloseFee(BigDecimal closeFee) { this.closeFee = closeFee; }
    public void setCloseReason(ViopCloseReason closeReason) { this.closeReason = closeReason; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public void setRealizedReturnPercent(BigDecimal realizedReturnPercent) { this.realizedReturnPercent = realizedReturnPercent; }
    public void setNote(String note) { this.note = note; }

    public static ManualViopPosition createNew(
            User user,
            String symbol,
            String displayName,
            ViopCategory viopCategory,
            String underlyingSymbol,
            ViopDirection direction,
            BigDecimal contractCount,
            BigDecimal entryPrice,
            LocalDate entryDate,
            BigDecimal currentPrice,
            BigDecimal contractMultiplier,
            BigDecimal initialMargin,
            LocalDate expiryDate,
            String note
    ) {
        ManualViopPosition p = new ManualViopPosition();
        p.user = user;
        p.symbol = symbol;
        p.displayName = displayName;
        p.viopCategory = viopCategory;
        p.underlyingSymbol = underlyingSymbol;
        p.direction = direction;
        p.contractCount = contractCount;
        p.entryPrice = entryPrice;
        p.entryDate = entryDate;
        p.currentPrice = currentPrice;
        p.contractMultiplier = contractMultiplier;
        p.initialMargin = initialMargin;
        p.expiryDate = expiryDate;
        p.status = ViopPositionStatus.OPEN;
        p.note = note;
        return p;
    }
}
