package com.nurseli.notificationservice.infrastructure.finance;

/**
 * finance-service {@code /internal/users/by-sub} yanıtının bildirim servisi tarafındaki karşılığı.
 */
public record FinanceUserInfoResponse(
        String sub,
        String email,
        boolean emailVerified
) {}