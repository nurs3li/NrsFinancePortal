package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiEmailDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * PortfolioAiEmailDeliveryEntity için JPA repository.
 */
public interface PortfolioAiEmailDeliveryRepository extends JpaRepository<PortfolioAiEmailDeliveryEntity, Long> {

    Optional<PortfolioAiEmailDeliveryEntity> findByUser_Id(Long userId);

    @Modifying
    @Query("DELETE FROM PortfolioAiEmailDeliveryEntity e WHERE e.user.id = :userId")
    void deleteByUser_Id(@Param("userId") Long userId);
}
