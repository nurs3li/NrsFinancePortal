package com.nurseli.notificationservice.controller;

import com.nurseli.notificationservice.email.GmailClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications/internal")
public class InternalEmailController {

    private final GmailClient gmailClient;

    @PostMapping("/email/send")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> sendEmail(@RequestBody SendEmailRequest request) {
        gmailClient.sendEmail(request.to(), request.subject(), request.body());
        log.info("[INTERNAL_EMAIL] mail sent to={}", request.to());
        return ResponseEntity.accepted().build();
    }

    public record SendEmailRequest(
            @NotBlank @Email String to,
            @NotBlank String subject,
            @NotBlank String body
    ) {}
}
