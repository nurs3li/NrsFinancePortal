package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.PublicRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/register")
public class PublicRegistrationController {

    private final PublicRegistrationService publicRegistrationService;

    @PostMapping("/request-code")
    public ResponseEntity<ApiResponse<String>> requestCode(@RequestBody RequestCodeBody body) {
        publicRegistrationService.requestCode(body.email());
        return ResponseEntity.ok(ApiResponse.success("Doğrulama kodu gönderildi."));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<String>> complete(@RequestBody CompleteRegistrationBody body) {
        publicRegistrationService.completeRegistration(body.email(), body.username(), body.password(), body.code());
        return ResponseEntity.ok(ApiResponse.success("Kayıt tamamlandı. Giriş yapabilirsiniz."));
    }

    public record RequestCodeBody(String email) {}

    public record CompleteRegistrationBody(String email, String username, String password, String code) {}
}
