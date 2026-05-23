package com.nurseli.notificationservice.api;

import com.nurseli.notificationservice.application.NotificationService;
import com.nurseli.notificationservice.domain.Notification;
import com.nurseli.notificationservice.infrastructure.security.CurrentUserSubResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private CurrentUserSubResolver currentUserSubResolver;

    @Test
    void getUnreadCount_returnsCount() throws Exception {
        when(currentUserSubResolver.getRequiredSub()).thenReturn("sub-me");
        when(notificationService.getUnreadCount("sub-me")).thenReturn(4L);

        mockMvc.perform(get("/api/notifications/me/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    void markAsRead_found_returns204() throws Exception {
        when(currentUserSubResolver.getRequiredSub()).thenReturn("sub-me");
        when(notificationService.markRead(5L, "sub-me")).thenReturn(true);

        mockMvc.perform(patch("/api/notifications/5/read")).andExpect(status().isNoContent());
    }

    @Test
    void markAsRead_notFound_returns404() throws Exception {
        when(currentUserSubResolver.getRequiredSub()).thenReturn("sub-me");
        when(notificationService.markRead(5L, "sub-me")).thenReturn(false);

        mockMvc.perform(patch("/api/notifications/5/read")).andExpect(status().isNotFound());
    }

    @Test
    void getMyNotifications_mapsPageContent() throws Exception {
        when(currentUserSubResolver.getRequiredSub()).thenReturn("sub-me");
        Notification n =
                Notification.builder()
                        .id(1L)
                        .userSub("sub-me")
                        .title("Hi")
                        .body("Body")
                        .type("ALERT")
                        .createdAt(Instant.now())
                        .occurrenceCount(1)
                        .build();
        when(notificationService.findByUserSub(eq("sub-me"), any(Pageable.class), eq(false)))
                .thenReturn(new PageImpl<>(List.of(n)));

        mockMvc.perform(get("/api/notifications/me").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Hi"));

        verify(notificationService).findByUserSub(eq("sub-me"), any(Pageable.class), eq(false));
    }
}
