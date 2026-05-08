package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.RiskMonitorUserResponse;
import com.nurseli.nrsfinanceportal.common.dto.RiskMonitorUserDetailResponse;
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

    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'ADMIN')")
    @GetMapping("/monitor/users")
    public List<RiskMonitorUserResponse> monitorUsers() {
        return service.getRiskMonitorUsers();
    }

    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'ADMIN')")
    @GetMapping("/monitor/users/{userId}/detail")
    public RiskMonitorUserDetailResponse monitorUserDetail(@PathVariable Long userId) {
        return service.getRiskMonitorUserDetail(userId);
    }

}
