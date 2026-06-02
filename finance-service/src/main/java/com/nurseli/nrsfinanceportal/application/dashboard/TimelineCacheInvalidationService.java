package com.nurseli.nrsfinanceportal.application.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * finance-service timeline önbellek servisi — kullanıcıya ait Redis timeline anahtarlarını temizler.
 */
@RequiredArgsConstructor
@Service

public class TimelineCacheInvalidationService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * {@code invalidateUserTimeline} — timeline:{userId}:* desenindeki tüm Redis anahtarlarını siler.
     */
    public void invalidateUserTimeline(Long userId) {

        String pattern = "timeline:" + userId + ":*";
    Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
