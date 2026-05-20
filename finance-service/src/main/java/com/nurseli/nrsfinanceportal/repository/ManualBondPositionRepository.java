package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.bond.BondPositionStatus;
import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManualBondPositionRepository extends JpaRepository<ManualBondPosition, Long> {

    List<ManualBondPosition> findByUser_IdAndStatusNotOrderByBuyDateDesc(Long userId, BondPositionStatus status);

    List<ManualBondPosition> findByUser_IdAndStatusOrderByBuyDateDesc(Long userId, BondPositionStatus status);

    Optional<ManualBondPosition> findByIdAndUser_Id(Long id, Long userId);
}
