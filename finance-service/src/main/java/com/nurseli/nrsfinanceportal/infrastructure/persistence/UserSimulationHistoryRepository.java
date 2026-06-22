package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.simulation.UserSimulationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * UserSimulationHistory entity için JPA repository.
 */
public interface UserSimulationHistoryRepository extends JpaRepository<UserSimulationHistory, String> {

    List<UserSimulationHistory> findByUser_IdOrderBySavedAtDesc(Long userId);

    Optional<UserSimulationHistory> findByIdAndUser_Id(String id, Long userId);

    long countByUser_Id(Long userId);

    void deleteByIdAndUser_Id(String id, Long userId);
}
