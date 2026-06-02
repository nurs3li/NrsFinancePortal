package com.nurseli.nrsfinanceportal.application.admin;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * finance-service admin bildirim yardımcısı — yöneticilere Kafka üzerinden sistem bildirimi gönderir ve tür başına 5 dakikalık cooldown uygular.
 */
@Slf4j
@RequiredArgsConstructor
@Component

public class AdminNotificationHelper {

    private static final long COOLDOWN_MS = 5 * 60 * 1000L;

    private final NotificationEventKafkaPublisher publisher;
    private final UserRepository userRepository;

    private final ConcurrentHashMap<String, AtomicReference<Instant>> cooldowns = new ConcurrentHashMap<>();

    /**
     * {@code notifyAdmins} — Belirtilen tür, başlık ve gövde ile tüm ADMIN rolündeki kullanıcılara NotificationRequestedEvent yayımlar; aynı tür için cooldown süresince tekrar göndermez.
     */
    public void notifyAdmins(String type, String title, String body) {
        try {
            AtomicReference<Instant> ref = cooldowns.computeIfAbsent(type,
                    k -> new AtomicReference<>(Instant.EPOCH));
            Instant now = Instant.now();
            Instant last = ref.get();
            if (now.toEpochMilli() - last.toEpochMilli() < COOLDOWN_MS) {
                return;
            }
            if (!ref.compareAndSet(last, now)) {
                return;
            }

            userRepository.findByRole(Role.ADMIN).forEach(admin ->
                    publisher.publish(new NotificationRequestedEvent(
                            admin.getKeycloakUserId(),
                            title,
                            body,
                            type,
                            "system",
                            null
                    ))
            );
            log.info("[ADMIN_NOTIFY] type={} sent to all admins", type);
        } catch (Exception ex) {
            log.error("[ADMIN_NOTIFY] Failed to send {} notification", type, ex);
        }
    }
}