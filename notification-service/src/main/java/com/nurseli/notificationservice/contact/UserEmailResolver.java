package com.nurseli.notificationservice.contact;

import com.nurseli.notificationservice.config.NotificationEmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEmailResolver {

    private final FinanceUserClient financeUserClient;
    private final NotificationEmailProperties notificationEmailProperties;

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