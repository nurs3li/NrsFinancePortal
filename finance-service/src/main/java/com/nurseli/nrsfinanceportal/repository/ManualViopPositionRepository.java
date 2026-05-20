package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManualViopPositionRepository extends JpaRepository<ManualViopPosition, Long> {

    List<ManualViopPosition> findByUser_IdAndStatusNotOrderByEntryDateDesc(Long userId, ViopPositionStatus status);

    List<ManualViopPosition> findByUser_IdAndStatusOrderByEntryDateDesc(Long userId, ViopPositionStatus status);

    Optional<ManualViopPosition> findByIdAndUser_Id(Long id, Long userId);
}
