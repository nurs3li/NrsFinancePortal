package com.nurseli.notificationservice.email;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailDeliveryAuditRepository extends JpaRepository<EmailDeliveryAudit, Long> {
}