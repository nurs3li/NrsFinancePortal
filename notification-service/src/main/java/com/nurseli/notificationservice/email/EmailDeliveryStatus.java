package com.nurseli.notificationservice.email;

public enum EmailDeliveryStatus {
    SENT,
    SKIPPED_POLICY,
    SKIPPED_RATE_LIMIT,
    SKIPPED_DEDUP,
    FAILED_PROVIDER
}