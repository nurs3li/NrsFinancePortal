package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioAsset;
import com.nurseli.nrsfinanceportal.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioAssetRepository
        extends JpaRepository<PortfolioAsset, Long> {

    List<PortfolioAsset> findByUser(User user);


    Optional<PortfolioAsset> findByUserAndTypeAndSymbol(
            User user,
            AssetType type,
            String symbol);
}
