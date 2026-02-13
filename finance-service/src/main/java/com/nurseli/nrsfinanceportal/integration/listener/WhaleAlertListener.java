package com.nurseli.nrsfinanceportal.integration.listener;

import com.nurseli.nrsfinanceportal.integration.kafka.KafkaTopics;
import com.nurseli.nrsfinanceportal.integration.kafka.event.WhaleAlertTriggeredEvent;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.repository.WhaleHistoryRepository;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import com.nurseli.nrsfinanceportal.service.TimelineCacheInvalidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhaleAlertListener {

    private final UserRepository userRepository;
    private final WhaleHistoryRepository whaleHistoryRepository;
    private final TimelineCacheInvalidationService timelineCacheInvalidationService;

    // 🔥 EKLENEN TEK ŞEY
    private final RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(
            topics = KafkaTopics.WHALE_ALERT_TRIGGERED,
            groupId = "finance-whale-consumer-v15",
            containerFactory = "whaleAlertKafkaListenerContainerFactory"  // 🔥 EKLENEN
    )

    public void onWhaleAlert(WhaleAlertTriggeredEvent event) {

        if (event.whaleLevel() == null) {
            log.error("❌ Legacy whale event ignored (null level): {}", event);
            return;
        }

        log.warn("""
                        🐋 WHALE ALERT RECEIVED
                        userId : {}
                        level  : {}
                        reason : AUTO_ALERT
                        """,
                event.userId(),
                event.whaleLevel()
        );

        Long userId = Long.valueOf(event.userId());

        var user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException("User not found for userId=" + userId)
                );

        // 1️⃣ USER FLAG (MEVCUT)
        user.markAsWhale(
                event.whaleLevel(),
                event.triggeredAt()
        );
        userRepository.save(user);

        // 2️⃣ HISTORY (MEVCUT)
        whaleHistoryRepository.save(
                WhaleHistory.of(
                        user.getId(),
                        event.whaleLevel(),
                        event.impactScore(),
                        "AUTO_ALERT",
                        event.triggeredAt()
                )
        );

        // 🔥 3️⃣ REDIS → SON WHALE STATE (YENİ, AYNI YERDE)
        String redisKey = "whale:last:" + user.getId();

        redisTemplate.opsForValue().set(
                redisKey,
                Map.of(
                        "level", event.whaleLevel().name(),
                        "impactScore", event.impactScore(),
                        "triggeredAt", event.triggeredAt().toString()
                )
        );

        log.info("🧠 Whale last state written to Redis for user {}", user.getId());

        // 4️⃣ TIMELINE CACHE INVALIDATION (MEVCUT)
        timelineCacheInvalidationService.invalidateUserTimeline(user.getId());

        log.info("🧹 Timeline cache invalidated for user {}", user.getId());
    }
}
