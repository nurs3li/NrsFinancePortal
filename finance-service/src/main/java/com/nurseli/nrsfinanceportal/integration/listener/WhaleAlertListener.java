package com.nurseli.nrsfinanceportal.integration.listener;

import com.nurseli.nrsfinanceportal.domain.whale.WhaleLevel;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import com.nurseli.nrsfinanceportal.integration.kafka.KafkaTopics;
import com.nurseli.nrsfinanceportal.integration.kafka.event.WhaleAlertTriggeredEvent;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.repository.WhaleHistoryRepository;
import com.nurseli.nrsfinanceportal.service.AdminNotificationHelper;
import com.nurseli.nrsfinanceportal.service.ReviewTaskService;
import com.nurseli.nrsfinanceportal.service.TimelineCacheInvalidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class WhaleAlertListener {

    private final UserRepository userRepository;
    private final WhaleHistoryRepository whaleHistoryRepository;
    private final TimelineCacheInvalidationService timelineCacheInvalidationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ReviewTaskService reviewTaskService;
    private final AdminNotificationHelper adminNotificationHelper;

    private final AtomicInteger whaleAlertCounter = new AtomicInteger(0);
    private final AtomicReference<Instant> windowStart = new AtomicReference<>(Instant.now());
    private static final int WHALE_SPIKE_THRESHOLD = 5;
    private static final long WHALE_SPIKE_WINDOW_MS = 60 * 60 * 1000L;

    public WhaleAlertListener(UserRepository userRepository,
                              WhaleHistoryRepository whaleHistoryRepository,
                              TimelineCacheInvalidationService timelineCacheInvalidationService,
                              RedisTemplate<String, Object> redisTemplate,
                              ReviewTaskService reviewTaskService,
                              AdminNotificationHelper adminNotificationHelper) {
        this.userRepository = userRepository;
        this.whaleHistoryRepository = whaleHistoryRepository;
        this.timelineCacheInvalidationService = timelineCacheInvalidationService;
        this.redisTemplate = redisTemplate;
        this.reviewTaskService = reviewTaskService;
        this.adminNotificationHelper = adminNotificationHelper;
    }

    @KafkaListener(
            topics = KafkaTopics.WHALE_ALERT_TRIGGERED,
            groupId = "finance-whale-consumer-v15",
            containerFactory = "whaleAlertKafkaListenerContainerFactory"
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

        user.markAsWhale(event.whaleLevel(), event.triggeredAt());
        userRepository.save(user);

        whaleHistoryRepository.save(
                WhaleHistory.ofDetailed(
                        user.getId(),
                        event.whaleLevel(),
                        event.impactScore(),
                        "AUTO_ALERT",
                        event.dailyVolume(),
                        event.hourlyTransactionCount(),
                        event.maxSingleTransaction(),
                        event.pattern(),
                        event.behavior(),
                        event.risk(),
                        event.triggeredAt()
                )
        );

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

        timelineCacheInvalidationService.invalidateUserTimeline(user.getId());
        log.info("🧹 Timeline cache invalidated for user {}", user.getId());

        if (event.whaleLevel() == WhaleLevel.L2_WHALE || event.whaleLevel() == WhaleLevel.L3_MEGA_WHALE) {
            reviewTaskService.createFromWhaleAlert(user.getId());
            log.info("[TASK] Created WHALE_REVIEW task for user {} level {}", user.getId(), event.whaleLevel());
        }

        // WHALE_SPIKE — ADMIN bildirimi
        Instant now = Instant.now();
        Instant start = windowStart.get();
        if (now.toEpochMilli() - start.toEpochMilli() > WHALE_SPIKE_WINDOW_MS) {
            windowStart.compareAndSet(start, now);
            whaleAlertCounter.set(1);
        } else {
            int count = whaleAlertCounter.incrementAndGet();
            if (count == WHALE_SPIKE_THRESHOLD) {
                adminNotificationHelper.notifyAdmins(
                        "WHALE_SPIKE",
                        "Whale uyarısı yoğunluğu",
                        "Merhaba,\n\nSon bir saat içinde "
                                + count
                                + " adet whale uyarısı tetiklendi. Piyasa veya kullanıcı aktivitesinde olağan dışı yoğunluk olabilir.\n\n"
                                + "Lütfen Admin panelinden durumu kontrol ediniz.\n\nNRS Finance Portal"
                );
            }
        }
    }
}