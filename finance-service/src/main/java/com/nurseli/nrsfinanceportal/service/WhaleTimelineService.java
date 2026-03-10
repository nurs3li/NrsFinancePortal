package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.WhaleTimelineResponse;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import com.nurseli.nrsfinanceportal.repository.WhaleHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WhaleTimelineService {

    private final WhaleHistoryRepository repository;

    public List<WhaleTimelineResponse> getTimeline(Long userId) {
        return repository
                .findByUserIdOrderByTriggeredAtDesc(userId)
                .stream()
                .map(h -> new WhaleTimelineResponse(
                        h.getId(),
                        h.getUserId(),
                        h.getWhaleLevel().name(),
                        h.getImpactScore(),
                        h.getReason(),
                        h.getDailyVolume(),
                        h.getHourlyTransactionCount(),
                        h.getMaxSingleTransaction(),
                        h.getPattern(),
                        h.getBehavior(),
                        h.getRisk(),
                        h.getTriggeredAt(),
                        h.getCreatedAt()
                ))
                .toList();
    }
}
