package com.nurseli.notificationservice.controller;

import com.nurseli.notificationservice.dto.NotificationDto;
import com.nurseli.notificationservice.dto.UnreadCountResponse;
import com.nurseli.notificationservice.security.CurrentUserSubResolver;
import com.nurseli.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserSubResolver currentUserSubResolver;

    @GetMapping("/me")
    public Page<NotificationDto> getMyNotifications(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        String sub = currentUserSubResolver.getRequiredSub();
        return notificationService.findByUserSub(sub, pageable, unreadOnly)
                .map(NotificationDto::from);
    }

    @GetMapping("/me/unread-count")
    public UnreadCountResponse getUnreadCount() {
        String sub = currentUserSubResolver.getRequiredSub();
        long count = notificationService.getUnreadCount(sub);
        return new UnreadCountResponse(count);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        String sub = currentUserSubResolver.getRequiredSub();
        boolean updated = notificationService.markRead(id, sub);
        return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}