package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ManualViopPosition entity için JPA repository.
 */
public interface ManualViopPositionRepository extends JpaRepository<ManualViopPosition, Long> {

    List<ManualViopPosition> findByUser_IdAndStatusNotOrderByEntryDateDesc(Long userId, ViopPositionStatus status);

    List<ManualViopPosition> findByUser_IdAndStatusOrderByEntryDateDesc(Long userId, ViopPositionStatus status);

    boolean existsByUser_IdAndStatus(Long userId, ViopPositionStatus status);

    Optional<ManualViopPosition> findByIdAndUser_Id(Long id, Long userId);

    @Modifying
    @Query("DELETE FROM ManualViopPosition p WHERE p.user.id = :userId")
    void deleteByUser_Id(@Param("userId") Long userId);
}
