package com.nurseli.nrsfinanceportal.domain.user;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Kullanıcının yıldızladığı varlık entity; marketType ve symbol ile benzersiz takip.
 */
@Entity
@Table(
        name = "user_starred_asset",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_starred_asset_user_symbol_type", columnNames = {"user_id", "market_type", "symbol"}),
                @UniqueConstraint(name = "uq_user_starred_asset_user_position", columnNames = {"user_id", "position"})
        }
)
public class UserStarredAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "market_type", nullable = false, length = 24)
    private String marketType;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserStarredAsset() {
    }

    public static UserStarredAsset of(User user, String marketType, String symbol, int position) {
        UserStarredAsset row = new UserStarredAsset();
        row.user = user;
        row.marketType = marketType;
        row.symbol = symbol;
        row.position = position;
        row.createdAt = Instant.now();
        return row;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getMarketType() {
        return marketType;
    }

    public String getSymbol() {
        return symbol;
    }

    public Integer getPosition() {
        return position;
    }
}
