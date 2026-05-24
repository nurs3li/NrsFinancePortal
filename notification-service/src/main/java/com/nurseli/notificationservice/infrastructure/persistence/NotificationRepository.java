package com.nurseli.notificationservice.infrastructure.persistence;

import com.nurseli.notificationservice.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link Notification} varlığı için Spring Data JPA erişim katmanı.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** {@code findByUserSubOrderByCreatedAtDesc} — Kullanıcının tüm bildirimlerini yeniden eskiye sayfalar. */
    Page<Notification> findByUserSubOrderByCreatedAtDesc(String userSub, Pageable pageable);

    /** {@code findByUserSubAndReadAtIsNullOrderByCreatedAtDesc} — Okunmamış bildirimleri yeniden eskiye sayfalar. */
    Page<Notification> findByUserSubAndReadAtIsNullOrderByCreatedAtDesc(String userSub, Pageable pageable);

    /** {@code countByUserSubAndReadAtIsNull} — Kullanıcının okunmamış bildirim sayısını döner. */
    long countByUserSubAndReadAtIsNull(String userSub);

    /** {@code findByIdAndUserSub} — Kimliği ve kullanıcı {@code sub} değeri eşleşen bildirimi arar. */
    Optional<Notification> findByIdAndUserSub(Long id, String userSub);

    /**
     * {@code findFirstByUserSubAndTypeAndReadAtIsNullOrderByCreatedAtDesc} — Aynı türde okunmamış
     * en son bildirimi bulur; tekrarlayan olayları birleştirmek için kullanılır.
     */
    Optional<Notification> findFirstByUserSubAndTypeAndReadAtIsNullOrderByCreatedAtDesc(
            String userSub, String type);
}