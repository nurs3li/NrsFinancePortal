package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioTimeseriesSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManualPortfolioTimeseriesSnapshotRepository
        extends JpaRepository<ManualPortfolioTimeseriesSnapshot, ManualPortfolioTimeseriesSnapshot.IdKey> {

    List<ManualPortfolioTimeseriesSnapshot> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
