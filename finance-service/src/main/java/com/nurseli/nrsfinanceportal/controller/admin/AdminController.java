package com.nurseli.nrsfinanceportal.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @GetMapping("/ping")
    public String ping() {
        return "ADMIN OK 🛡️";
    }

    @GetMapping("/users")
    public String listUsersDummy() {
        return "Admin can see users (dummy for now)";
    }
}
