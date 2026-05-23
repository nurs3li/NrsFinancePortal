package com.nurseli.notificationservice.application;

import com.nurseli.notificationservice.domain.Notification;
import com.nurseli.notificationservice.infrastructure.persistence.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void create_nonDedupType_insertsNewRow() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });

        Notification saved =
                notificationService.create("sub-1", "T", "B", "PRICE_ALERT_TRIGGERED", "ALERT", 9L);

        assertThat(saved.getType()).isEqualTo("PRICE_ALERT_TRIGGERED");
        assertThat(saved.getOccurrenceCount()).isEqualTo(1);
        verify(notificationRepository, never())
                .findFirstByUserSubAndTypeAndReadAtIsNullOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void create_dedupEligibleType_updatesExistingUnread() {
        Notification existing =
                Notification.builder()
                        .id(5L)
                        .userSub("sub-1")
                        .title("Old")
                        .body("Old body")
                        .type("REAL_RETURN_NEGATIVE")
                        .occurrenceCount(2)
                        .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                        .build();

        when(notificationRepository.findFirstByUserSubAndTypeAndReadAtIsNullOrderByCreatedAtDesc(
                        "sub-1", "REAL_RETURN_NEGATIVE"))
                .thenReturn(Optional.of(existing));
        when(notificationRepository.save(existing)).thenReturn(existing);

        Notification result =
                notificationService.create("sub-1", "New", "New body", "REAL_RETURN_NEGATIVE", "P", 1L);

        assertThat(result.getTitle()).isEqualTo("New");
        assertThat(result.getOccurrenceCount()).isEqualTo(3);
        assertThat(result.getLastOccurredAt()).isNotNull();
    }

    @Test
    void findByUserSub_unreadOnly_usesUnreadQuery() {
        PageRequest page = PageRequest.of(0, 20);
        when(notificationRepository.findByUserSubAndReadAtIsNullOrderByCreatedAtDesc("sub-1", page))
                .thenReturn(new PageImpl<>(List.of()));

        notificationService.findByUserSub("sub-1", page, true);

        verify(notificationRepository).findByUserSubAndReadAtIsNullOrderByCreatedAtDesc("sub-1", page);
        verify(notificationRepository, never()).findByUserSubOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void markRead_found_setsReadAtAndReturnsTrue() {
        Notification n =
                Notification.builder().id(1L).userSub("sub-1").title("t").type("X").build();
        when(notificationRepository.findByIdAndUserSub(1L, "sub-1")).thenReturn(Optional.of(n));
        when(notificationRepository.save(n)).thenReturn(n);

        assertThat(notificationService.markRead(1L, "sub-1")).isTrue();
        assertThat(n.getReadAt()).isNotNull();
    }

    @Test
    void markRead_notFound_returnsFalse() {
        when(notificationRepository.findByIdAndUserSub(99L, "sub-1")).thenReturn(Optional.empty());
        assertThat(notificationService.markRead(99L, "sub-1")).isFalse();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void getUnreadCount_delegatesToRepository() {
        when(notificationRepository.countByUserSubAndReadAtIsNull("sub-1")).thenReturn(7L);
        assertThat(notificationService.getUnreadCount("sub-1")).isEqualTo(7L);
    }
}
