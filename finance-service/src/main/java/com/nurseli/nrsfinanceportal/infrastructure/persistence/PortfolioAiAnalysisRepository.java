package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PortfolioAiAnalysisEntity için JPA repository.
 */
public interface PortfolioAiAnalysisRepository extends JpaRepository<PortfolioAiAnalysisEntity, String> {

    Optional<PortfolioAiAnalysisEntity> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<PortfolioAiAnalysisEntity> findByIdAndUser_Id(String id, Long userId);

    List<PortfolioAiAnalysisEntity> findByUser_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long userId,
            Instant from
    );

    long countByUser_IdAndCreatedAtGreaterThanEqual(Long userId, Instant from);

    long countByUser_IdAndCreatedAtGreaterThanEqualAndModelNot(
            Long userId,
            Instant from,
            String model
    );

    @Modifying
    @Query("DELETE FROM PortfolioAiAnalysisEntity e WHERE e.user.id = :userId")
    void deleteByUser_Id(@Param("userId") Long userId);
}
