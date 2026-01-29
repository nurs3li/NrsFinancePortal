package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import com.nurseli.nrsfinanceportal.repository.WhaleHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WhaleTimelineService {

    private final WhaleHistoryRepository repository;

    public List<WhaleHistory> getTimeline(Long userId) {
        return repository.findByUserIdOrderByTriggeredAtDesc(userId);
    }
}
