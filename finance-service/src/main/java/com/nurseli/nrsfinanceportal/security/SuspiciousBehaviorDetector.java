package com.nurseli.nrsfinanceportal.security;

import com.nurseli.nrsfinanceportal.config.SuspiciousProperties;
import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.integration.kafka.event.SuspiciousActivityDetectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuspiciousBehaviorDetector {

    private static final String KEY_PREFIX = "suspicious:user:";

    private final StringRedisTemplate redisTemplate;
    private final SuspiciousProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onTransactionCreated(TransactionCreatedEvent event) {

        if (!properties.isEnabled()) return;

        Long userId = event.userId();
        String key = KEY_PREFIX + userId + ":count";

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) count = 1L;
            if (count == 1) {
                redisTemplate.expire(key, 1, TimeUnit.MINUTES);
            }

            boolean highFreq = count > properties.getMaxTransactionsPerMinute();
            boolean highAmount = event.amount().compareTo(properties.getHighAmountThreshold()) > 0;

            if (highFreq) {
                log.warn(
                        "[SUSPICIOUS] HIGH_FREQUENCY userId={} txId={} countLastMin={} threshold={}",
                        userId,
                        event.transactionId(),
                        count,
                        properties.getMaxTransactionsPerMinute()
                );
                eventPublisher.publishEvent(new SuspiciousActivityDetectedEvent(
                        userId,
                        event.transactionId(),
                        "HIGH_FREQUENCY",
                        event.amount(),
                        count,
                        null,
                        properties.getMaxTransactionsPerMinute(),
                        Instant.now()
                ));
            }
            if (highAmount) {
                log.warn(
                        "[SUSPICIOUS] HIGH_AMOUNT userId={} txId={} amount={} threshold={}",
                        userId,
                        event.transactionId(),
                        event.amount(),
                        properties.getHighAmountThreshold()
                );
                eventPublisher.publishEvent(new SuspiciousActivityDetectedEvent(
                        userId,
                        event.transactionId(),
                        "HIGH_AMOUNT",
                        event.amount(),
                        null,
                        properties.getHighAmountThreshold(),
                        null,
                        Instant.now()
                ));
            }
        } catch (Exception e) {
            log.error("[SUSPICIOUS] Check failed for userId={}", userId, e);
        }
    }
}