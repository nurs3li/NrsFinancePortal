package com.nurseli.notificationservice.infrastructure.persistence;

import com.nurseli.notificationservice.domain.email.EmailDeliveryAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link EmailDeliveryAudit} varlığı için Spring Data JPA erişim katmanı.
 */
public interface EmailDeliveryAuditRepository extends JpaRepository<EmailDeliveryAudit, Long> {
}