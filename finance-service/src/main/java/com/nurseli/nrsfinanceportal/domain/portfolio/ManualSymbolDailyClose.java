package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "manual_symbol_daily_close")
public class ManualSymbolDailyClose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    @Column(name = "symbol", nullable = false, length = 30)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "close_try", nullable = false, precision = 38, scale = 8)
    private BigDecimal closeTry;

    @Column(name = "price_source", length = 40)
    private String priceSource;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected ManualSymbolDailyClose() {
    }

    public static ManualSymbolDailyClose of(
            User user,
            AssetType assetType,
            String symbol,
            LocalDate tradeDate,
            BigDecimal closeTry,
            String priceSource,
            Instant fetchedAt
    ) {
        ManualSymbolDailyClose row = new ManualSymbolDailyClose();
        row.user = user;
        row.assetType = assetType;
        row.symbol = symbol;
        row.tradeDate = tradeDate;
        row.closeTry = closeTry;
        row.priceSource = priceSource;
        row.fetchedAt = fetchedAt;
        return row;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public BigDecimal getCloseTry() {
        return closeTry;
    }

    public String getPriceSource() {
        return priceSource;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
