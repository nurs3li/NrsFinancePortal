package com.nurseli.notificationservice.contact;

public record FinanceUserInfoResponse(
        String sub,
        String email,
        boolean emailVerified
) {}