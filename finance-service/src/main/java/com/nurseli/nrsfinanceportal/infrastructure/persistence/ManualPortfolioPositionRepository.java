package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ManualPortfolioPosition entity için JPA repository.
 */
public interface ManualPortfolioPositionRepository extends JpaRepository<ManualPortfolioPosition, Long> {

    List<ManualPortfolioPosition> findByUserIdOrderByBuyDateAsc(Long userId);

    List<ManualPortfolioPosition> findByUser_IdAndStatusOrderByBuyDateAsc(Long userId, ManualPositionStatus status);

    Optional<ManualPortfolioPosition> findByIdAndUser_Id(Long id, Long userId);

    @Modifying
    @Query("DELETE FROM ManualPortfolioPosition p WHERE p.user.id = :userId")
    void deleteByUser_Id(@Param("userId") Long userId);
}