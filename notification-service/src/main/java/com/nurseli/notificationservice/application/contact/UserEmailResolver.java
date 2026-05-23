package com.nurseli.notificationservice.application.contact;

import com.nurseli.notificationservice.config.NotificationEmailProperties;
import com.nurseli.notificationservice.infrastructure.finance.FinanceUserClient;
import com.nurseli.notificationservice.infrastructure.finance.FinanceUserInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Keycloak kullanıcı kimliği ({@code sub}) için finance-service üzerinden e-posta adresini çözer.
 * {@link NotificationEmailProperties#isRequireFinanceEmailVerified()} bayrağına göre doğrulanmış e-posta zorunluluğu uygulanır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEmailResolver {

    private final FinanceUserClient financeUserClient;
    private final NotificationEmailProperties notificationEmailProperties;

    /**
     * {@code resolveEmail} — Verilen {@code sub} için finance-service'ten e-posta adresini döner;
     * doğrulama veya adres eksikliğinde {@code null} döner.
     */
    public String resolveEmail(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            return null;
        }

        try {
            FinanceUserInfoResponse u = financeUserClient.getBySub(userSub);
            if (u == null) {
                log.debug("[UserEmailResolver] No finance user for sub={}", userSub);
                return null;
            }

            if (notificationEmailProperties.isRequireFinanceEmailVerified() && !u.emailVerified()) {
                log.info("[UserEmailResolver] Email not verified for sub={}, skipping email channel", userSub);
                return null;
            }

            if (!u.emailVerified()) {
                log.debug("[UserEmailResolver] Using email despite finance emailVerified=false for sub={}", userSub);
            }

            if (u.email() == null || u.email().isBlank()) {
                log.warn("[UserEmailResolver] Email missing for sub={}, skipping email channel", userSub);
                return null;
            }

            return u.email();
        } catch (Exception e) {
            log.error("[UserEmailResolver] Failed to resolve email for sub={}", userSub, e);
            return null;
        }
    }
}