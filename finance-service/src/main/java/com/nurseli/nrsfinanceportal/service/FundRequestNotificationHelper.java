package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.fund.FundRequest;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundRequestNotificationHelper {

    private final NotificationEventKafkaPublisher publisher;
    private final UserRepository userRepository;

    public void notifyCreatedForFinanceManagers(FundRequest request) {
        try {
            String title = "Yeni para talebi";
            String body = "%s talebi oluşturuldu: %s %s (requestId=%d)"
                    .formatted(
                            request.getType().name(),
                            request.getAmount().toPlainString(),
                            request.getCurrency(),
                            request.getId()
                    );

            userRepository.findByRole(Role.FINANCE_MANAGER).forEach(fm ->
                    publisher.publish(new NotificationRequestedEvent(
                            fm.getKeycloakUserId(),
                            title,
                            body,
                            "FUND_REQUEST_CREATED",
                            "fund_request",
                            request.getId()
                    ))
            );
        } catch (Exception ex) {
            log.error("[FUND_REQUEST][NOTIFY] created notify failed. requestId={}", request.getId(), ex);
        }
    }

    public void notifyApproved(FundRequest request) {
        try {
            String sub = request.getUser().getKeycloakUserId();
            String title = "Talebiniz onaylandı";
            String body = "%s talebiniz onaylandı. Tutar: %s %s".formatted(
                    request.getType().name(),
                    request.getAmount().toPlainString(),
                    request.getCurrency()
            );
            publisher.publish(new NotificationRequestedEvent(
                    sub,
                    title,
                    body,
                    "FUND_REQUEST_APPROVED",
                    "fund_request",
                    request.getId()
            ));
        } catch (Exception ex) {
            log.error("[FUND_REQUEST][NOTIFY] approved notify failed. requestId={}", request.getId(), ex);
        }
    }

    public void notifyRejected(FundRequest request) {
        try {
            String sub = request.getUser().getKeycloakUserId();
            String title = "Talebiniz reddedildi";
            String body = "%s talebiniz reddedildi. Tutar: %s %s%s".formatted(
                    request.getType().name(),
                    request.getAmount().toPlainString(),
                    request.getCurrency(),
                    request.getReviewNote() != null && !request.getReviewNote().isBlank()
                            ? " — Not: " + request.getReviewNote()
                            : ""
            );
            publisher.publish(new NotificationRequestedEvent(
                    sub,
                    title,
                    body,
                    "FUND_REQUEST_REJECTED",
                    "fund_request",
                    request.getId()
            ));
        } catch (Exception ex) {
            log.error("[FUND_REQUEST][NOTIFY] rejected notify failed. requestId={}", request.getId(), ex);
        }
    }
}