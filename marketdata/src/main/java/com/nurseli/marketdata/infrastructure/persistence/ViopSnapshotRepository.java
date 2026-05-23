package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.viop.ViopSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ViopSnapshotRepository extends JpaRepository<ViopSnapshotEntity, Long> {

    Optional<ViopSnapshotEntity> findTopByContractCodeOrderByUpdateDateDesc(String contractCode);

    Optional<ViopSnapshotEntity> findByContractCodeAndUpdateDateAndSource(
            String contractCode, LocalDateTime updateDate, String source);
}
