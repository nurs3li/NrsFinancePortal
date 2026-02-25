package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.WhaleTimelineResponse;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import com.nurseli.nrsfinanceportal.service.WhaleTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/whales")
@RequiredArgsConstructor
public class WhaleTimelineController {

    private final WhaleTimelineService service;

    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'ADMIN')")
    @GetMapping("/{userId}/timeline")
    public List<WhaleTimelineResponse> timeline(@PathVariable Long userId) {
        return service.getTimeline(userId);
    }

}
