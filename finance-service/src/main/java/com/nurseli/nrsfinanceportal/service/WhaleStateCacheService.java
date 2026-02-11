package com.nurseli.nrsfinanceportal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WhaleStateCacheService {

    private final RedisTemplate<String, String> redisTemplate; // 🔥 STRING
    private final ObjectMapper objectMapper;

    public WhaleState getLastWhaleState(Long userId) {

        String rawJson = redisTemplate.opsForValue()
                .get("whale:last:" + userId);

        if (rawJson == null) {
            return null;
        }

        try {
            return objectMapper.readValue(rawJson, WhaleState.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Invalid whale JSON in Redis for userId=" + userId +
                            ", value=" + rawJson,
                    e
            );
        }
    }

    public record WhaleState(
            String level,
            Integer impactScore,
            Instant triggeredAt
    ) {}
}
