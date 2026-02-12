package com.nurseli.whaleanalytics.infrastructure.redis;

import com.nurseli.whaleanalytics.domain.WhaleMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;
import com.nurseli.whaleanalytics.domain.TransactionSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;
import java.util.Set;


@Repository
@RequiredArgsConstructor
public class RedisWhaleReadRepository {

    private final StringRedisTemplate redisTemplate;

    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration ONE_DAY = Duration.ofHours(24);

    public void incrementHourlyCount(Long userId) {
        String key = "whale:user:" + userId + ":count:1h";
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, ONE_HOUR);
    }

    public void addDailyVolume(Long userId, BigDecimal amount) {
        String key = "whale:user:" + userId + ":volume:24h";
        redisTemplate.opsForValue().increment(key, amount.longValue());
        redisTemplate.expire(key, ONE_DAY);
    }

    public void updateMaxTransaction(Long userId, BigDecimal amount) {
        String key = "whale:user:" + userId + ":max_tx";

        String current = redisTemplate.opsForValue().get(key);
        if (current == null || amount.compareTo(new BigDecimal(current)) > 0) {
            redisTemplate.opsForValue().set(key, amount.toPlainString(), ONE_DAY);
        }
    }
    public WhaleMetrics getMetrics(String userId) {

        String hourlyCountKey = "whale:user:" + userId + ":count:1h";
        String dailyVolumeKey = "whale:user:" + userId + ":volume:24h";
        String maxTxKey = "whale:user:" + userId + ":max_tx";

        String hourlyCountRaw = redisTemplate.opsForValue().get(hourlyCountKey);
        String dailyVolumeRaw = redisTemplate.opsForValue().get(dailyVolumeKey);
        String maxTxRaw = redisTemplate.opsForValue().get(maxTxKey);

        int hourlyCount = hourlyCountRaw != null ? Integer.parseInt(hourlyCountRaw) : 0;
        BigDecimal dailyVolume = dailyVolumeRaw != null
                ? new BigDecimal(dailyVolumeRaw)
                : BigDecimal.ZERO;
        BigDecimal maxTx = maxTxRaw != null
                ? new BigDecimal(maxTxRaw)
                : BigDecimal.ZERO;

        return new WhaleMetrics(
                dailyVolume,
                hourlyCount,
                maxTx
        );
    }
    public List<TransactionSnapshot> getRecentTransactions(String userId, Duration lookback) {

        String key = "whale:tx:" + userId;

        Instant now = Instant.now();
        long nowMillis = now.toEpochMilli();
        long fromMillis = now.minus(lookback).toEpochMilli();

        // DÖNÜŞ TİPİNİ Object DEĞİL String OLARAK BEKLE
        Set<ZSetOperations.TypedTuple<String>> raw =
                redisTemplate.opsForZSet().rangeByScoreWithScores(
                        key,
                        (double) fromMillis,
                        (double) nowMillis
                );

        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        return raw.stream()
                .map(tuple -> {
                    String value = tuple.getValue();      // amount as string
                    Double score = tuple.getScore();      // epoch millis (Double)

                    if (value == null || score == null) {
                        return null;
                    }

                    BigDecimal amount = new BigDecimal(value);
                    Instant occurredAt = Instant.ofEpochMilli(score.longValue());

                    return new TransactionSnapshot(amount, occurredAt);
                })
                .filter(s -> s != null)
                .toList();
    }

}
