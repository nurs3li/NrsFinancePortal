package com.nurseli.notificationservice.domain.policy;

/**
 * Bildirim türüne göre hangi kanalların kullanılacağını belirler;
 * {@code NotificationChannelPolicyResolver} bu kararı üretir.
 */
public enum DeliveryDecision {

    /** Yalnızca uygulama içi bildirim; e-posta gönderilmez. */
    IN_APP_ONLY,

    /** Hem uygulama içi bildirim hem de e-posta kanalı etkin. */
    IN_APP_AND_EMAIL
}