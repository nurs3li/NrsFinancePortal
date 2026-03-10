package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.suspicious.SuspiciousEvent;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.integration.kafka.event.SuspiciousActivityDetectedEvent;
import com.nurseli.nrsfinanceportal.repository.SuspiciousEventRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuspiciousEventService {

    private final SuspiciousEventRepository repository;
    private final ReviewTaskService reviewTaskService;
    private final NotificationEventKafkaPublisher notificationEventKafkaPublisher;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSuspiciousActivity(SuspiciousActivityDetectedEvent event) {

        try {
            SuspiciousEvent e = SuspiciousEvent.of(
                    event.userId(),
                    event.transactionId(),
                    event.reason(),
                    event.amount(),
                    event.countInWindow(),
                    event.thresholdAmount(),
                    event.thresholdCount(),
                    event.occurredAt()
            );

            SuspiciousEvent saved = repository.save(e);
            reviewTaskService.createFromSuspiciousEvent(saved.getId());

            userRepository.findById(event.userId()).ifPresent(u ->
                    notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                            u.getKeycloakUserId(),
                            "Şüpheli aktivite tespit edildi",
                            "Hesabınızda olağan dışı bir işlem tespit edildi. Güvenlik incelemesi başlatıldı.",
                            "SUSPICIOUS_ACTIVITY",
                            "suspicious_event",
                            saved.getId()
                    ))
            );

            log.info("[SUSPICIOUS][DB] persisted id={} userId={} txId={} reason={}",
                    saved.getId(), event.userId(), event.transactionId(), event.reason());
        } catch (Exception ex) {
            log.error("[SUSPICIOUS][DB] FAILED userId={} txId={} reason={}",
                    event.userId(), event.transactionId(), event.reason(), ex);
            throw ex;
        }
    }

    public List<SuspiciousEvent> getRecent(int limit) {
        return repository.findAllByOrderByOccurredAtDesc(
                org.springframework.data.domain.PageRequest.of(0, limit)
        );
    }

    public List<SuspiciousEvent> getByUser(Long userId) {
        return repository.findByUserIdOrderByOccurredAtDesc(userId);
    }
}