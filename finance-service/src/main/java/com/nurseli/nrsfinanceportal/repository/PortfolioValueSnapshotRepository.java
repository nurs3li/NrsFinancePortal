package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioValueSnapshot;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PortfolioValueSnapshotRepository extends JpaRepository<PortfolioValueSnapshot, Long> {

    List<PortfolioValueSnapshot> findByUser_IdAndSnapshotAtBetweenOrderBySnapshotAtAsc(
            Long userId,
            Instant from,
            Instant to
    );

    @Query("""
            SELECT COUNT(s) > 0 FROM PortfolioValueSnapshot s
            WHERE s.user.id = :userId
              AND s.triggerType = :trigger
              AND s.snapshotAt >= :start
              AND s.snapshotAt < :end
            """)
    boolean existsByUserAndTriggerBetween(
            @Param("userId") Long userId,
            @Param("trigger") SnapshotTriggerType trigger,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
