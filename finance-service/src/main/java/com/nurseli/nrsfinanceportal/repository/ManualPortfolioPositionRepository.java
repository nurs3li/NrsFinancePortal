package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManualPortfolioPositionRepository extends JpaRepository<ManualPortfolioPosition, Long> {

    List<ManualPortfolioPosition> findByUserIdOrderByBuyDateAsc(Long userId);
}