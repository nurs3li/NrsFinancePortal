package com.nurseli.notificationservice.service;

import com.nurseli.notificationservice.domain.Notification;
import com.nurseli.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    private static final Set<String> DEDUP_ELIGIBLE_TYPES = Set.of(
            "SUSPICIOUS_ACTIVITY",
            "ACCOUNT_FROZEN",
            "ACCOUNT_UNFROZEN",
            "REVIEW_TASK_CREATED",
            "REVIEW_COMPLETED"
    );

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

    public Page<Notification> findByUserSub(String userSub, Pageable pageable, boolean unreadOnly) {
        if (unreadOnly) {
            return notificationRepository.findByUserSubAndReadAtIsNullOrderByCreatedAtDesc(userSub, pageable);
        }
        return notificationRepository.findByUserSubOrderByCreatedAtDesc(userSub, pageable);
    }

    public long getUnreadCount(String userSub) {
        return notificationRepository.countByUserSubAndReadAtIsNull(userSub);
    }

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