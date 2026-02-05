package com.nurseli.nrsfinanceportal.integration.listener;

import com.nurseli.nrsfinanceportal.integration.kafka.event.WhaleAlertTriggeredEvent;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.repository.WhaleHistoryRepository;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
@Slf4j
public class WhaleAlertListener {

    private final UserRepository userRepository;
    private final WhaleHistoryRepository whaleHistoryRepository;

    @KafkaListener(
            topics = "whale.alert.triggered",
            groupId = "finance-whale-consumer-v2"
    )
    public void onWhaleAlert(WhaleAlertTriggeredEvent event) {

        log.warn("""
                        🐋 WHALE ALERT RECEIVED
                        userId : {}
                        level  : {}
                        reason : AUTO_ALERT
                        """,
                event.userId(),
                event.level()
        );

        // ✅ String → Long dönüşüm (KRİTİK SATIR)
        Long userId = Long.valueOf(event.userId());

        var user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException("User not found for userId=" + userId)
                );

        // 1️⃣ User flag
        user.markAsWhale(
                event.level(),
                event.triggeredAt()
        );
        userRepository.save(user);

        // 2️⃣ History
        whaleHistoryRepository.save(
                WhaleHistory.of(
                        user.getId(),
                        event.level(),
                        "AUTO_ALERT",
                        event.triggeredAt()
                )
        );
    }
}
