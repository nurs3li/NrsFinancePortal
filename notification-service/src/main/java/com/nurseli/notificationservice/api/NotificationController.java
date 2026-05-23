package com.nurseli.notificationservice.api;



import com.nurseli.notificationservice.api.dto.NotificationDto;

import com.nurseli.notificationservice.api.dto.UnreadCountResponse;

import com.nurseli.notificationservice.infrastructure.security.CurrentUserSubResolver;

import com.nurseli.notificationservice.application.NotificationService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

import org.springframework.data.web.PageableDefault;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;



/**

 * Oturum açmış kullanıcılar için bildirim REST endpoint'lerini sunar.

 */

@RestController

@RequestMapping("/api/notifications")

@RequiredArgsConstructor

public class NotificationController {



    private final NotificationService notificationService;

    private final CurrentUserSubResolver currentUserSubResolver;



    /**

     * {@code getMyNotifications} — Oturum açmış kullanıcının bildirimlerini sayfalı olarak döner;

     * isteğe bağlı olarak yalnızca okunmamış kayıtları filtreler.

     */

    @GetMapping("/me")

    public Page<NotificationDto> getMyNotifications(

            @PageableDefault(size = 20) Pageable pageable,

            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {

        String sub = currentUserSubResolver.getRequiredSub();

        return notificationService.findByUserSub(sub, pageable, unreadOnly)

                .map(NotificationDto::from);

    }



    /**

     * {@code getUnreadCount} — Oturum açmış kullanıcının okunmamış bildirim sayısını döner.

     */

    @GetMapping("/me/unread-count")

    public UnreadCountResponse getUnreadCount() {

        String sub = currentUserSubResolver.getRequiredSub();

        long count = notificationService.getUnreadCount(sub);

        return new UnreadCountResponse(count);

    }



    /**

     * {@code markAsRead} — Belirtilen bildirimi okundu olarak işaretler; kayıt bulunamazsa 404 döner.

     */

    @PatchMapping("/{id}/read")

    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {

        String sub = currentUserSubResolver.getRequiredSub();

        boolean updated = notificationService.markRead(id, sub);

        return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();

    }

}

