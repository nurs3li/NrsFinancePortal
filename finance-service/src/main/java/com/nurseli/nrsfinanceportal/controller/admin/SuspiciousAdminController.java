package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.SuspiciousEventView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.suspicious.SuspiciousEvent;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.service.SuspiciousEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/suspicious")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
public class SuspiciousAdminController {

    private final SuspiciousEventService suspiciousEventService;
    private final UserRepository userRepository;

    /**
     * Son N suspicious event’i döner (panel tablosu için).
     */
    @GetMapping("/events")
    public ApiResponse<List<SuspiciousEventView>> listRecent(
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {

        List<SuspiciousEvent> events = suspiciousEventService.getRecent(limit);

        // Kullanıcı bilgilerini tek seferde çek
        List<Long> userIds = events.stream()
                .map(SuspiciousEvent::getUserId)
                .distinct()
                .toList();

        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<SuspiciousEventView> views = events.stream()
                .map(e -> SuspiciousEventView.of(e, usersById.get(e.getUserId())))
                .toList();

        return ApiResponse.success(views);
    }

    /**
     * Belirli bir kullanıcının tüm suspicious event’leri.
     */
    @GetMapping("/users/{userId}/events")
    public ApiResponse<List<SuspiciousEventView>> listByUser(
            @PathVariable Long userId
    ) {

        List<SuspiciousEvent> events = suspiciousEventService.getByUser(userId);

        User user = userRepository.findById(userId)
                .orElse(null);

        List<SuspiciousEventView> views = events.stream()
                .map(e -> SuspiciousEventView.of(e, user))
                .toList();

        return ApiResponse.success(views);
    }
}