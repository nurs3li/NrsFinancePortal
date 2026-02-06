package com.nurseli.nrsfinanceportal.domain.trade;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
@Entity
@Table(name = "trade")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ======================
       OWNER
       ====================== */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /* ======================
       TRADE INTENT
       ====================== */
    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false)
    private TradeType tradeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    /* ======================
       FK (SOURCE OF TRUTH)
       ====================== */
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private Long transactionId;

    /* ======================
       READ-ONLY RELATION
       ====================== */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "transaction_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false
    )
    private Transaction transaction;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Trade() {}

    private Trade(
            User user,
            TradeType tradeType,
            AssetType assetType,
            String symbol,
            BigDecimal quantity,
            Long transactionId
    ) {
        this.user = user;
        this.tradeType = tradeType;
        this.assetType = assetType;
        this.symbol = symbol;
        this.quantity = quantity;
        this.transactionId = transactionId;
        this.createdAt = Instant.now();
    }

    public static Trade create(
            User user,
            TradeType tradeType,
            AssetType assetType,
            String symbol,
            BigDecimal quantity,
            Long transactionId
    ) {
        return new Trade(
                user,
                tradeType,
                assetType,
                symbol,
                quantity,
                transactionId
        );
    }

    /* ======================
       GETTERS
       ====================== */

    public Long getId() { return id; }
    public User getUser() { return user; }
    public TradeType getTradeType() { return tradeType; }
    public AssetType getAssetType() { return assetType; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public Long getTransactionId() { return transactionId; }
    public Transaction getTransaction() { return transaction; }
    public Instant getCreatedAt() { return createdAt; }
}



