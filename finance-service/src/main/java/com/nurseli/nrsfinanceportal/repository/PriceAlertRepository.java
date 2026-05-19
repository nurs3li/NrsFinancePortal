package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlert;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT a FROM PriceAlert a JOIN FETCH a.user WHERE a.status = :status")
    List<PriceAlert> findByStatusWithUser(@Param("status") PriceAlertStatus status);

    long countByUserIdAndStatus(Long userId, PriceAlertStatus status);

    @Query("SELECT COUNT(a) FROM PriceAlert a WHERE a.user.id = :userId AND a.status = :status AND a.assetType IN :types")
    long countByUser_IdAndStatusAndAssetTypeIn(
            @Param("userId") Long userId,
            @Param("status") PriceAlertStatus status,
            @Param("types") Collection<AssetType> types);
}
