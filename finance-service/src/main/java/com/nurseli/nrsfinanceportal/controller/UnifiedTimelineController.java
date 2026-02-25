package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.TimelinePageResponse;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.TimelineReadModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.Instant;

@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
public class UnifiedTimelineController {

    private final TimelineReadModelService timelineReadModelService;
    private final CurrentUserResolver currentUserResolver;

    @PreAuthorize("hasAnyRole('USER', 'FINANCE_MANAGER', 'ADMIN')")
    @GetMapping
    public TimelinePageResponse timeline(
            @RequestParam(required = false) Instant cursorAt,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {

        Long userId = currentUserResolver
                .getOrCreateCurrentUser()
                .getId();

        return timelineReadModelService.getTimeline(
                userId,
                cursorAt,
                cursorId,
                size
        );
    }
}
