package com.nurseli.marketdata.domain.derivatives;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "derivative_contract")
public class DerivativeContract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String contractCode;

    @Column(nullable = false, length = 32)
    private String underlying;

    @Column(nullable = false, length = 16)
    private String expiry;

    @Column(nullable = false, length = 16)
    private String type;

    /** Liste % değişimi (CSV backfill sonrası bir kez hesaplanır; 1 takvim günü penceresi). */
    @Column(name = "viop_list_pct_change_1d", precision = 14, scale = 6)
    private BigDecimal viopListPctChange1d;
    @Column(name = "viop_list_pct_change_7d", precision = 14, scale = 6)
    private BigDecimal viopListPctChange7d;
    @Column(name = "viop_list_pct_change_30d", precision = 14, scale = 6)
    private BigDecimal viopListPctChange30d;
    @Column(name = "viop_list_pct_change_365d", precision = 14, scale = 6)
    private BigDecimal viopListPctChange365d;
    @Column(name = "viop_rollups_computed_at")
    private LocalDateTime viopRollupsComputedAt;

    /** Son iki snapshot fiyatına göre % ((son − önceki) / önceki); CSV rollup ile doldurulur. */
    @Column(name = "viop_seq_move_pct", precision = 14, scale = 6)
    private BigDecimal viopSeqMovePct;
    @Column(name = "viop_seq_move_trend", length = 16)
    private String viopSeqMoveTrend;

    public Long getId() { return id; }
    public String getContractCode() { return contractCode; }
    public void setContractCode(String contractCode) { this.contractCode = contractCode; }
    public String getUnderlying() { return underlying; }
    public void setUnderlying(String underlying) { this.underlying = underlying; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getViopListPctChange1d() { return viopListPctChange1d; }
    public void setViopListPctChange1d(BigDecimal viopListPctChange1d) { this.viopListPctChange1d = viopListPctChange1d; }
    public BigDecimal getViopListPctChange7d() { return viopListPctChange7d; }
    public void setViopListPctChange7d(BigDecimal viopListPctChange7d) { this.viopListPctChange7d = viopListPctChange7d; }
    public BigDecimal getViopListPctChange30d() { return viopListPctChange30d; }
    public void setViopListPctChange30d(BigDecimal viopListPctChange30d) { this.viopListPctChange30d = viopListPctChange30d; }
    public BigDecimal getViopListPctChange365d() { return viopListPctChange365d; }
    public void setViopListPctChange365d(BigDecimal viopListPctChange365d) { this.viopListPctChange365d = viopListPctChange365d; }
    public LocalDateTime getViopRollupsComputedAt() { return viopRollupsComputedAt; }
    public void setViopRollupsComputedAt(LocalDateTime viopRollupsComputedAt) { this.viopRollupsComputedAt = viopRollupsComputedAt; }

    public BigDecimal getViopSeqMovePct() { return viopSeqMovePct; }
    public void setViopSeqMovePct(BigDecimal viopSeqMovePct) { this.viopSeqMovePct = viopSeqMovePct; }
    public String getViopSeqMoveTrend() { return viopSeqMoveTrend; }
    public void setViopSeqMoveTrend(String viopSeqMoveTrend) { this.viopSeqMoveTrend = viopSeqMoveTrend; }
}
