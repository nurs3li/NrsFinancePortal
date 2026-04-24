package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "portfolio_value_snapshots")
public class PortfolioValueSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private SnapshotTriggerType triggerType;

    @Column(name = "trade_id")
    private Long tradeId;

    @Column(name = "combined_value_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal combinedValueTry;

    @Column(name = "combined_cost_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal combinedCostTry;

    @Column(name = "combined_pnl_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal combinedPnlTry;

    @Column(name = "trade_value_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal tradeValueTry;

    @Column(name = "trade_cost_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal tradeCostTry;

    @Column(name = "trade_pnl_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal tradePnlTry;

    @Column(name = "manual_value_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal manualValueTry;

    @Column(name = "manual_cost_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal manualCostTry;

    @Column(name = "manual_pnl_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal manualPnlTry;

    protected PortfolioValueSnapshot() {
    }

    public static PortfolioValueSnapshot create(
            User user,
            Instant snapshotAt,
            SnapshotTriggerType triggerType,
            Long tradeId,
            BigDecimal combinedValueTry,
            BigDecimal combinedCostTry,
            BigDecimal combinedPnlTry,
            BigDecimal tradeValueTry,
            BigDecimal tradeCostTry,
            BigDecimal tradePnlTry,
            BigDecimal manualValueTry,
            BigDecimal manualCostTry,
            BigDecimal manualPnlTry
    ) {
        PortfolioValueSnapshot s = new PortfolioValueSnapshot();
        s.user = user;
        s.snapshotAt = snapshotAt;
        s.triggerType = triggerType;
        s.tradeId = tradeId;
        s.combinedValueTry = combinedValueTry;
        s.combinedCostTry = combinedCostTry;
        s.combinedPnlTry = combinedPnlTry;
        s.tradeValueTry = tradeValueTry;
        s.tradeCostTry = tradeCostTry;
        s.tradePnlTry = tradePnlTry;
        s.manualValueTry = manualValueTry;
        s.manualCostTry = manualCostTry;
        s.manualPnlTry = manualPnlTry;
        return s;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getSnapshotAt() {
        return snapshotAt;
    }

    public SnapshotTriggerType getTriggerType() {
        return triggerType;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public BigDecimal getCombinedValueTry() {
        return combinedValueTry;
    }

    public BigDecimal getCombinedCostTry() {
        return combinedCostTry;
    }

    public BigDecimal getCombinedPnlTry() {
        return combinedPnlTry;
    }

    public BigDecimal getTradeValueTry() {
        return tradeValueTry;
    }

    public BigDecimal getTradeCostTry() {
        return tradeCostTry;
    }

    public BigDecimal getTradePnlTry() {
        return tradePnlTry;
    }

    public BigDecimal getManualValueTry() {
        return manualValueTry;
    }

    public BigDecimal getManualCostTry() {
        return manualCostTry;
    }

    public BigDecimal getManualPnlTry() {
        return manualPnlTry;
    }
}
