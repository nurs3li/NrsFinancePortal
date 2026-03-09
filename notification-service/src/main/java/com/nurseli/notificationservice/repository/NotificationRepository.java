package com.nurseli.notificationservice.repository;

import com.nurseli.notificationservice.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserSubOrderByCreatedAtDesc(String userSub, Pageable pageable);

    Page<Notification> findByUserSubAndReadAtIsNullOrderByCreatedAtDesc(String userSub, Pageable pageable);

    long countByUserSubAndReadAtIsNull(String userSub);

    Optional<Notification> findByIdAndUserSub(Long id, String userSub);
}