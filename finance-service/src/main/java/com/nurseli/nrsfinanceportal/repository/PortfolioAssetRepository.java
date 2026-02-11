package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioAsset;
import com.nurseli.nrsfinanceportal.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
public interface PortfolioAssetRepository
        extends JpaRepository<PortfolioAsset, Long> {

    // ✅ DOĞRU
    List<PortfolioAsset> findByUser(User user);

    Optional<PortfolioAsset> findByUserAndTypeAndSymbol(
            User user,
            AssetType type,
            String symbol
    );

    // 🔥 DAĞILIM (assetType bazlı, miktar)
    @Query("""
        select p.type, sum(p.quantity)
        from PortfolioAsset p
        where p.user.id = :userId
        group by p.type
    """)
    List<Object[]> calculateDistribution(Long userId);
}
