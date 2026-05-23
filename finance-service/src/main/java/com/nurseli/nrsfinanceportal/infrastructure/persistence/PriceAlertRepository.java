package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlert;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * PriceAlert entity için JPA repository.
 */
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<PriceAlert> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<PriceAlert> findByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, PriceAlertStatus status, Pageable pageable);

    Page<PriceAlert> findByUser_IdAndStatusInOrderByCreatedAtDesc(
            Long userId,
            Collection<PriceAlertStatus> statuses,
            Pageable pageable);

    @Query("SELECT a FROM PriceAlert a JOIN FETCH a.user WHERE a.status = :status")
    List<PriceAlert> findByStatusWithUser(@Param("status") PriceAlertStatus status);

    long countByUserIdAndStatus(Long userId, PriceAlertStatus status);

    @Modifying
    @Query("DELETE FROM PriceAlert a WHERE a.user.id = :userId")
    void deleteByUser_Id(@Param("userId") Long userId);

    @Query("SELECT COUNT(a) FROM PriceAlert a WHERE a.user.id = :userId AND a.status = :status AND a.assetType IN :types")
    long countByUser_IdAndStatusAndAssetTypeIn(
            @Param("userId") Long userId,
            @Param("status") PriceAlertStatus status,
            @Param("types") Collection<AssetType> types);
}
