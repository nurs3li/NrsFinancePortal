package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManualPortfolioPositionRepository extends JpaRepository<ManualPortfolioPosition, Long> {

    List<ManualPortfolioPosition> findByUserIdOrderByBuyDateAsc(Long userId);

    List<ManualPortfolioPosition> findByUser_IdAndStatusOrderByBuyDateAsc(Long userId, ManualPositionStatus status);

    Optional<ManualPortfolioPosition> findByIdAndUser_Id(Long id, Long userId);
}