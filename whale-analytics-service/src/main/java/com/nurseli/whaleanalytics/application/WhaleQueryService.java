package com.nurseli.whaleanalytics.application;

import com.nurseli.whaleanalytics.domain.WhaleDecisionEngine;
import com.nurseli.whaleanalytics.domain.WhaleResult;
import com.nurseli.whaleanalytics.infrastructure.redis.RedisWhaleReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WhaleQueryService {

    private final RedisWhaleReadRepository readRepository;
    private final WhaleDecisionEngine decisionEngine;

    public WhaleResult getWhaleStatus(Long userId) {

        var metrics = readRepository.getMetrics(String.valueOf(userId));
        var level = decisionEngine.evaluate(metrics);

        return new WhaleResult(
                String.valueOf(userId),
                level,
                metrics,
                Instant.now()
        );

    }
}
