package com.nurseli.metricsservice.controller;

import com.nurseli.metricsservice.api.dto.DashboardMetricsDto;
import com.nurseli.metricsservice.api.dto.MetricsResponse;
import com.nurseli.metricsservice.opensearch.MetricsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final MetricsQueryService metricsQueryService;

    @GetMapping("/dashboard")
    public MetricsResponse<DashboardMetricsDto> dashboard() {
        DashboardMetricsDto dto = metricsQueryService.getDashboard();
        return MetricsResponse.ok(dto);
    }
}