package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * İlk Keycloak oturumu ile DB'de kullanıcı oluşturulduğunda yöneticilere bilgi verir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegistrationNotificationHelper {

    private static final ZoneId TR = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(TR);

    private final NotificationEventKafkaPublisher publisher;
    private final UserRepository userRepository;

    public void notifyAdminsNewUser(User newUser, Instant registeredAt) {
        try {
            String title = "Yeni kullanıcı kaydı — " + safe(newUser.getEmail(), newUser.getUsername());
            String body = """
                    Merhaba,

                    Sisteme yeni bir kullanıcı ilk kez giriş yaparak kayıt oluşturdu.

                    • Kullanıcı ID: %d
                    • E-posta: %s
                    • Kullanıcı adı: %s
                    • Rol: %s
                    • E-posta doğrulandı: %s
                    • Keycloak subject: %s
                    • Kayıt zamanı: %s

                    İyi çalışmalar.
                    NRS Finance Portal
                    """.formatted(
                    newUser.getId(),
                    safe(newUser.getEmail(), "—"),
                    safe(newUser.getUsername(), "—"),
                    newUser.getRole() != null ? newUser.getRole().name() : "—",
                    newUser.isEmailVerified() ? "Evet" : "Hayır",
                    safe(newUser.getKeycloakUserId(), "—"),
                    TS.format(registeredAt));

            userRepository.findByRole(Role.ADMIN).forEach(admin ->
                    publisher.publish(new NotificationRequestedEvent(
                            admin.getKeycloakUserId(),
                            title,
                            body,
                            "USER_REGISTERED",
                            "user",
                            newUser.getId()
                    )));
        } catch (Exception ex) {
            log.error("[USER_REGISTERED] notify admins failed userId={}", newUser.getId(), ex);
        }
    }

    private static String safe(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
