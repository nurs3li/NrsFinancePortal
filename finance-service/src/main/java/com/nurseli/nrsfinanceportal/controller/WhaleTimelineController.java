package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import com.nurseli.nrsfinanceportal.service.WhaleTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/whales")
@RequiredArgsConstructor
public class WhaleTimelineController {

    private final WhaleTimelineService service;

    @GetMapping("/{userId}/timeline")
    public List<WhaleHistory> timeline(@PathVariable Long userId) {
        return service.getTimeline(userId);
    }
}
