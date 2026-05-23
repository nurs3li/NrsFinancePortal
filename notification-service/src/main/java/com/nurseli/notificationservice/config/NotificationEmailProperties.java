package com.nurseli.notificationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka → e-posta kanalı: finance DB'deki emailVerified bayrağı Keycloak ile her zaman senkron olmayabilir.
 * false iken, adres doluysa e-posta gönderilir (işlem bildirimleri kesilmez).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "notification.email")
public class NotificationEmailProperties {

    /**
     * {@code requireFinanceEmailVerified} — {@code true}: finance-service {@code /internal/users} yanıtında
     * {@code emailVerified} zorunlu (eski davranış). {@code false}: yalnızca e-posta adresi dolu olması yeterli.
     */
    private boolean requireFinanceEmailVerified = true;
}
