package com.nurseli.notificationservice.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis tabanlı e-posta/bildirim tekrarını engeller;
 * aynı anahtar TTL süresi boyunca yalnızca ilk çağrıya izin verir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDedupService {

    private final StringRedisTemplate redisTemplate;

    /**
     * {@code firstTime} — Aynı anahtar için TTL süresi boyunca yalnızca ilk çağrıda {@code true} döner;
     * Redis hatasında dedup uygulanmaz ve {@code true} döner.
     */
    public boolean firstTime(String key, Duration ttl) {
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", ttl);
            boolean first = Boolean.TRUE.equals(ok);
            if (!first) {
                log.info("[DEDUP] Duplicate suppressed for key={} ttl={}s", key, ttl.getSeconds());
            }
            return first;
        } catch (Exception e) {
            log.error("[DEDUP] Error for key={}, treating as first-time", key, e);
            return true; // Redis hatasında dedup yapma, devam et
        }
    }
}