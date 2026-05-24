package com.nurseli.notificationservice.application;

import com.nurseli.notificationservice.domain.Notification;
import com.nurseli.notificationservice.infrastructure.persistence.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Bildirim oluşturma, listeleme ve okundu işaretleme işlemlerini yönetir; dedup destekli tiplerde tekrarları birleştirir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    private static final Set<String> DEDUP_ELIGIBLE_TYPES = Set.of(
            "REAL_RETURN_NEGATIVE",
            "REAL_RETURN_POSITIVE",
            "PORTFOLIO_CONCENTRATION_RISK",
            "PORTFOLIO_EVALUATION_REPORT"
    );

    /**
     * {@code create} — Yeni bildirim oluşturur; dedup uygun tiplerde okunmamış kayıt varsa occurrence sayısını artırır.
     */
    @Transactional
    public Notification create(String userSub, String title, String body, String type,
                               String referenceType, Long referenceId) {

        if (DEDUP_ELIGIBLE_TYPES.contains(type)) {
            var existing = notificationRepository
                    .findFirstByUserSubAndTypeAndReadAtIsNullOrderByCreatedAtDesc(userSub, type);

            if (existing.isPresent()) {
                Notification n = existing.get();
                n.setTitle(title);
                n.setBody(body);
                n.setReferenceType(referenceType);
                n.setReferenceId(referenceId);
                n.setOccurrenceCount(n.getOccurrenceCount() + 1);
                n.setLastOccurredAt(Instant.now());
                log.info("[DEDUP] Updated notification id={} type={} sub={} count={}",
                        n.getId(), type, userSub, n.getOccurrenceCount());
                return notificationRepository.save(n);
            }
        }

        Notification n = Notification.builder()
                .userSub(userSub)
                .title(title)
                .body(body)
                .type(type)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .occurrenceCount(1)
                .createdAt(Instant.now())
                .lastOccurredAt(Instant.now())
                .build();
        return notificationRepository.save(n);
    }

    /**
     * {@code findByUserSub} — Kullanıcının bildirimlerini sayfalı olarak döner; {@code unreadOnly} ile okunmamış filtresi uygulanabilir.
     */
    public Page<Notification> findByUserSub(String userSub, Pageable pageable, boolean unreadOnly) {
        if (unreadOnly) {
            return notificationRepository.findByUserSubAndReadAtIsNullOrderByCreatedAtDesc(userSub, pageable);
        }
        return notificationRepository.findByUserSubOrderByCreatedAtDesc(userSub, pageable);
    }

    /**
     * {@code getUnreadCount} — Kullanıcının okunmamış bildirim sayısını döner.
     */
    public long getUnreadCount(String userSub) {
        return notificationRepository.countByUserSubAndReadAtIsNull(userSub);
    }

    /**
     * {@code markRead} — Bildirimi okundu olarak işaretler; kayıt bulunamazsa {@code false} döner.
     */
    @Transactional
    public boolean markRead(Long id, String userSub) {
        return notificationRepository.findByIdAndUserSub(id, userSub)
                .map(n -> {
                    n.setReadAt(Instant.now());
                    notificationRepository.save(n);
                    return true;
                })
                .orElse(false);
    }
}
