package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.FreezeRequest;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.service.UserLoginSuspensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserSuspensionController {

    private final UserLoginSuspensionService userLoginSuspensionService;
    private final NotificationEventKafkaPublisher notificationEventKafkaPublisher;
    private final UserRepository userRepository;

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
}
