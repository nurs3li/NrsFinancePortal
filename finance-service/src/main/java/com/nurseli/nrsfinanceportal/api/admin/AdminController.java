package com.nurseli.nrsfinanceportal.api.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Eski {@code /admin} yolu altında admin erişim doğrulama ve stub endpoint'leri sunar.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    /**
     * {@code ping} — Admin rolünün çalıştığını doğrulayan basit yanıt döner.
     */
    @GetMapping("/ping")
    public String ping() {
        return "ADMIN OK 🛡️";
    }

    /**
     * {@code listUsersDummy} — Kullanıcı listesi için geçici stub yanıtı döner.
     */
    @GetMapping("/users")
    public String listUsersDummy() {
        return "Admin can see users (dummy for now)";
    }
}
