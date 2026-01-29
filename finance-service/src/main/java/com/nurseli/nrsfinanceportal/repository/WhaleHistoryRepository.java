package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WhaleHistoryRepository
        extends JpaRepository<WhaleHistory, Long> {

    List<WhaleHistory> findByUserIdOrderByTriggeredAtDesc(Long userId);
}
