package com.nurseli.whaleanalytics.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class RedisWhaleRepository {

    private final StringRedisTemplate redisTemplate;

    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration ONE_DAY = Duration.ofDays(1);

    private static final String HOURLY_COUNT_KEY = "whale:user:%d:count:1h";
    private static final String DAILY_VOLUME_KEY = "whale:user:%d:volume:24h";
    private static final String MAX_TX_KEY      = "whale:user:%d:max_tx";

    public void incrementHourlyCount(Long userId) {
        String key = HOURLY_COUNT_KEY.formatted(userId);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, ONE_HOUR);
    }

    public void addDailyVolume(Long userId, BigDecimal amount) {
        String key = DAILY_VOLUME_KEY.formatted(userId);

        String current = redisTemplate.opsForValue().get(key);
        BigDecimal newValue =
                current == null
                        ? amount
                        : new BigDecimal(current).add(amount);

        redisTemplate.opsForValue()
                .set(key, newValue.toPlainString(), ONE_DAY);
    }

    public void updateMaxTransaction(Long userId, BigDecimal amount) {
        String key = MAX_TX_KEY.formatted(userId);

        String current = redisTemplate.opsForValue().get(key);
        if (current == null || amount.compareTo(new BigDecimal(current)) > 0) {
            redisTemplate.opsForValue()
                    .set(key, amount.toPlainString(), ONE_DAY);
        }
    }

    // 🔹 Transaction snapshot ZSET key
    private String txKey(Long userId) {
        return "whale:tx:" + userId;
    }

    /**
     * Her transaction geldiğinde:
     * - value = amount (string)
     * - score = occurredAt epoch millis
     * şeklinde ZSET'e yazar.
     */
    public void addTransactionSnapshot(Long userId, BigDecimal amount, Instant occurredAt) {
        String key = txKey(userId);
        String value = amount.toPlainString();

        redisTemplate.opsForZSet().add(
                key,
                value,
                occurredAt.toEpochMilli()
        );
    }
}