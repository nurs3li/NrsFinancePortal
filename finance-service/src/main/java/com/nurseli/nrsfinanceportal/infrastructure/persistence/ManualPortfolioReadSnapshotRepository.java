package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioReadSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualPortfolioReadSnapshotRepository extends JpaRepository<ManualPortfolioReadSnapshot, Long> {
}
