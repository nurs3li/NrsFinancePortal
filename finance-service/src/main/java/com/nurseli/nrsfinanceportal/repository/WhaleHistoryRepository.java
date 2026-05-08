package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhaleHistoryRepository
        extends JpaRepository<WhaleHistory, Long> {

    List<WhaleHistory> findByUserIdOrderByTriggeredAtDesc(Long userId);

    Optional<WhaleHistory> findTopByUserIdOrderByTriggeredAtDesc(Long userId);

    long countByUserId(Long userId);
}
