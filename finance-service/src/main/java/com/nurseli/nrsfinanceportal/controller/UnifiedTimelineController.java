package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.UnifiedTimelineDto;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.UnifiedTimelineQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
public class UnifiedTimelineController {

    private final UnifiedTimelineQueryService service;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public List<UnifiedTimelineDto> timeline() {

        Long userId = currentUserResolver
                .getOrCreateCurrentUser()
                .getId();

        return service.getTimeline(userId);
    }
}
