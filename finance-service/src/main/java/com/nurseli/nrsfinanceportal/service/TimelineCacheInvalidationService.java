package com.nurseli.nrsfinanceportal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class TimelineCacheInvalidationService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void invalidateUserTimeline(Long userId) {

        String pattern = "timeline:" + userId + ":*";

        Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
