package com.nurseli.notificationservice.api;



import com.nurseli.notificationservice.application.email.OutboundEmailService;

import jakarta.validation.Valid;

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



/**

 * Dahili servisler arası doğrudan e-posta gönderimi için internal endpoint'leri sunar.

 */

@Slf4j

@RestController

@RequiredArgsConstructor

@RequestMapping("/api/notifications/internal")

public class InternalEmailController {



    private final OutboundEmailService outboundEmailService;



    /**

     * {@code sendEmail} — Doğrulanmış istek gövdesiyle e-postayı kuyruğa alır ve 202 Accepted döner.

     */

    @PostMapping("/email/send")

    @PreAuthorize("isAuthenticated()")

    public ResponseEntity<Void> sendEmail(@Valid @RequestBody SendEmailRequest request) {

        outboundEmailService.sendDirect(request.to(), request.subject(), request.body());

        return ResponseEntity.accepted().build();

    }



    /**

     * Doğrudan e-posta gönderimi isteği DTO'su.

     */

    public record SendEmailRequest(

            @NotBlank @Email String to,

            @NotBlank String subject,

            @NotBlank String body

    ) {}

}

