package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
}
