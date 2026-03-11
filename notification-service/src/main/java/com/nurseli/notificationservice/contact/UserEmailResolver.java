package com.nurseli.notificationservice.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEmailResolver {

    private final FinanceUserClient financeUserClient;

    public String resolveEmail(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            return null;
        }

        try {
            FinanceUserInfoResponse u = financeUserClient.getBySub(userSub);
            if (u == null) return null;

            if (!u.emailVerified()) {
                log.info("[UserEmailResolver] Email not verified for sub={}, skipping email channel", userSub);
                return null;
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