package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.suspicious.SuspiciousEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuspiciousEventRepository extends JpaRepository<SuspiciousEvent, Long> {

    List<SuspiciousEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    List<SuspiciousEvent> findByUserIdOrderByOccurredAtDesc(Long userId);
}