package com.nurseli.notificationservice.service;

import com.nurseli.notificationservice.domain.Notification;
import com.nurseli.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification create(String userSub, String title, String body, String type,
                               String referenceType, Long referenceId) {
        Notification n = Notification.builder()
                .userSub(userSub)
                .title(title)
                .body(body)
                .type(type)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .createdAt(Instant.now())
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