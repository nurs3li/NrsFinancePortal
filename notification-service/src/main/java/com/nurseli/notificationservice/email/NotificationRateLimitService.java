package com.nurseli.notificationservice.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRateLimitService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Belirli bir anahtar için (ör. sub + type) pencere süresinde en fazla maxCount kez izin ver.
     */
    public boolean allow(String key, Duration window, long maxCount) {
        try {
            Long current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redisTemplate.expire(key, window);
            }
            boolean allowed = current != null && current <= maxCount;
            if (!allowed) {
                log.info("[RATE_LIMIT] key={} current={} max={} window={}s",
                        key, current, maxCount, window.getSeconds());
            }
            return allowed;
        } catch (Exception e) {
            log.error("[RATE_LIMIT] Error for key={}, allowing by default", key, e);
            return true; // Redis hatasında maili tamamen bloklamayalım
        }
    }
}