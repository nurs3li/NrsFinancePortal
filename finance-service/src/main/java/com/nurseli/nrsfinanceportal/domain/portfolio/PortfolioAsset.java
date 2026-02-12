package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(
        name = "portfolio_assets",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_asset_symbol",
                        columnNames = {"user_id", "symbol"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA için gerekli
public class PortfolioAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 👤 Varlığın Sahibi
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 📊 Asset türü (FX, CRYPTO, FUND, METAL, STOCK)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetType type;

    // 🔑 BTCUSDT, USDTRY, XAU_TRY, TCD vb.
    @Column(nullable = false, length = 30)
    private String symbol;

    // 📦 Miktar (hassas hesaplamalar için precision 38)
    @Column(nullable = false, precision = 38, scale = 8)
    private BigDecimal quantity;

    @Column(name = "avg_buy_price", precision = 38, scale = 8)
    private BigDecimal avgBuyPrice; // TRY

    // ✅ Factory Method: Domain güvenliğini burada sağlıyoruz
    private PortfolioAsset(User user, AssetType type, String symbol, BigDecimal quantity) {
        this.user = user;
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
    }
    public void setAvgBuyPrice(BigDecimal avgBuyPrice) {
        this.avgBuyPrice = avgBuyPrice;
    }

    public static PortfolioAsset create(User user, AssetType type, String symbol, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        return new PortfolioAsset(user, type, symbol, quantity);
    }

    // ===================== DOMAIN BEHAVIOR (İş Mantığı) =====================

    public void increase(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return;
        this.quantity = this.quantity.add(value);
    }

    public void decrease(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal next = this.quantity.subtract(value);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Insufficient asset quantity for " + symbol);
        }
        this.quantity = next;
    }

    // ===================== EQUALS & HASHCODE (JPA Güvenliği) =====================
    // Entity'lerin Set içinde veya Hibernate önbelleğinde doğru çalışması için şarttır.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortfolioAsset that)) return false;
        // Yeni oluşturulan (id'si olmayan) nesneler için iş anahtarı (business key) kullanıyoruz
        return Objects.equals(user != null ? user.getId() : null, that.user != null ? that.user.getId() : null) &&
                Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user != null ? user.getId() : null, symbol);
    }
}