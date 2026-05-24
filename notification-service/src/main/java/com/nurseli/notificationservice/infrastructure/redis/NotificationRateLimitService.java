package com.nurseli.notificationservice.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis tabanlı e-posta gönderim hız sınırlaması uygular;
 * pencere süresi içinde anahtar başına en fazla belirlenen sayıda isteğe izin verir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRateLimitService {

    private final StringRedisTemplate redisTemplate;

    /**
     * {@code allow} — Belirli bir anahtar (ör. {@code sub + type}) için pencere süresinde
     * en fazla {@code maxCount} kez {@code true} döner; Redis hatasında varsayılan olarak izin verir.
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