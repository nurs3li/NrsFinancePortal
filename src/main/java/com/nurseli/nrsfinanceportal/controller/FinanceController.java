package com.nurseli.nrsfinanceportal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/finance")
public class FinanceController {

    /**
     * Herkes erişebilir
     */
    @GetMapping("/public")
    public ResponseEntity<String> publicEndpoint() {
        return ResponseEntity.ok("PUBLIC OK");
    }

    /**
     * Sadece FINANCE_MANAGER
     */
    @GetMapping("/secure")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ResponseEntity<String> secureEndpoint() {
        return ResponseEntity.ok("FINANCE MANAGER OK 💸");
    }
}
