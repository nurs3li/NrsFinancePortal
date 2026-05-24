package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Portföy toplam değer anlık görüntüsü entity; snapshot tarihi ve tetikleyici tipini saklar.
 */
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

    @Column(name = "portfolio_value_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal portfolioValueTry;

    @Column(name = "portfolio_cost_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal portfolioCostTry;

    @Column(name = "portfolio_pnl_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal portfolioPnlTry;

    protected PortfolioValueSnapshot() {
    }

    public static PortfolioValueSnapshot create(
            User user,
            Instant snapshotAt,
            SnapshotTriggerType triggerType,
            BigDecimal portfolioValueTry,
            BigDecimal portfolioCostTry,
            BigDecimal portfolioPnlTry
    ) {
        PortfolioValueSnapshot s = new PortfolioValueSnapshot();
        s.user = user;
        s.snapshotAt = snapshotAt;
        s.triggerType = triggerType;
        s.portfolioValueTry = portfolioValueTry;
        s.portfolioCostTry = portfolioCostTry;
        s.portfolioPnlTry = portfolioPnlTry;
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

    public BigDecimal getPortfolioValueTry() {
        return portfolioValueTry;
    }

    public BigDecimal getPortfolioCostTry() {
        return portfolioCostTry;
    }

    public BigDecimal getPortfolioPnlTry() {
        return portfolioPnlTry;
    }
}
