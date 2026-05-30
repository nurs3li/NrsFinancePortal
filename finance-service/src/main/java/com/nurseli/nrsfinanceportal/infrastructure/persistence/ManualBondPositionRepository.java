package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.bond.BondPositionStatus;
import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ManualBondPosition entity için JPA repository.
 */
public interface ManualBondPositionRepository extends JpaRepository<ManualBondPosition, Long> {

    List<ManualBondPosition> findByUser_IdAndStatusNotOrderByBuyDateDesc(Long userId, BondPositionStatus status);

    List<ManualBondPosition> findByUser_IdAndStatusOrderByBuyDateDesc(Long userId, BondPositionStatus status);

    boolean existsByUser_IdAndStatus(Long userId, BondPositionStatus status);

    Optional<ManualBondPosition> findByIdAndUser_Id(Long id, Long userId);

    @Modifying
    @Query("DELETE FROM ManualBondPosition p WHERE p.user.id = :userId")
    void deleteByUser_Id(@Param("userId") Long userId);
}
