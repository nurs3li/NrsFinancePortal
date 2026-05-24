package com.nurseli.nrsfinanceportal.api.admin;

import com.nurseli.nrsfinanceportal.api.dto.FreezeRequest;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import com.nurseli.nrsfinanceportal.application.AdminUserDeletionService;
import com.nurseli.nrsfinanceportal.application.UserLoginSuspensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kullanıcı giriş askıya alma, kaldırma ve kalıcı silme admin endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/admin/users", "/api/admin/users"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserSuspensionController {

    private final UserLoginSuspensionService userLoginSuspensionService;
    private final AdminUserDeletionService adminUserDeletionService;
    private final NotificationEventKafkaPublisher notificationEventKafkaPublisher;
    private final UserRepository userRepository;

    /**
     * {@code suspendLogin} — Kullanıcı portal girişini askıya alır ve Kafka bildirimi yayınlar.
     */
    @PostMapping("/{userId}/suspend-login")
    public ResponseEntity<ApiResponse<String>> suspendLogin(
            @PathVariable Long userId,
            @RequestBody(required = false) FreezeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String reason = request != null && request.reason() != null ? request.reason() : "Giriş askıya alındı";
        userLoginSuspensionService.suspendLogin(userId, jwt.getSubject(), reason);

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            String suspBody = """
                    Merhaba,

                    Yönetici tarafından portal girişiniz geçici olarak askıya alınmıştır.

                    Gerekçe: %s

                    Sorularınız için destek ile iletişime geçebilirsiniz.

                    NRS Finance Portal
                    """.formatted(reason);
            notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                    user.getKeycloakUserId(),
                    "Portal girişiniz askıya alındı",
                    suspBody,
                    "USER_LOGIN_SUSPENDED",
                    "user",
                    userId
            ));
        }

        return ResponseEntity.ok(ApiResponse.success("OK"));
    }

    /**
     * {@code unsuspendLogin} — Askıya alınmış giriş kısıtını kaldırır ve kullanıcıyı bilgilendirir.
     */
    @PostMapping("/{userId}/unsuspend-login")
    public ResponseEntity<ApiResponse<String>> unsuspendLogin(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {
        userLoginSuspensionService.unsuspendLogin(userId, jwt.getSubject());

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                    user.getKeycloakUserId(),
                    "Portal giriş engeliniz kaldırıldı",
                    """
                            Merhaba,

                            Yönetici tarafından portal giriş kısıtlamanız kaldırılmıştır.

                            NRS Finance Portal
                            """,
                    "USER_LOGIN_UNSUSPENDED",
                    "user",
                    userId
            ));
        }

        return ResponseEntity.ok(ApiResponse.success("OK"));
    }

    /**
     * {@code deleteUser} — Kullanıcıyı Keycloak ve yerel veritabanından kalıcı olarak siler.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<String>> deleteUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {
        adminUserDeletionService.deleteUser(userId, jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Kullanıcı Keycloak ve portal veritabanından silindi."));
    }
}
