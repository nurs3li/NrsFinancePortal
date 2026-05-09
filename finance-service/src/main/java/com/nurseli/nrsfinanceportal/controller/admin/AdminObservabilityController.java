package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.config.ObservabilityProperties;
import com.nurseli.nrsfinanceportal.observability.GrafanaExploreUrlFactory;
import com.nurseli.nrsfinanceportal.observability.TempoTraceSummaryService;
import com.nurseli.nrsfinanceportal.observability.dto.GrafanaExploreUrlResponse;
import com.nurseli.nrsfinanceportal.observability.dto.TraceSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/observability")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminObservabilityController {

    private final ObservabilityProperties observabilityProperties;
    private final TempoTraceSummaryService tempoTraceSummaryService;
    private final GrafanaExploreUrlFactory grafanaExploreUrlFactory;

    @GetMapping("/traces/{traceId}/summary")
    public TraceSummaryResponse traceSummary(@PathVariable("traceId") String traceId) {
        return tempoTraceSummaryService.summarize(traceId);
    }

    @GetMapping("/explore/trace")
    public GrafanaExploreUrlResponse exploreTrace(@RequestParam("traceId") String traceId) {
        if (!observabilityProperties.isEnabled()) {
            return GrafanaExploreUrlResponse.disabled("app.observability.enabled=false");
        }
        if (traceId == null || traceId.isBlank()) {
            return new GrafanaExploreUrlResponse(true, "traceId_required", null);
        }
        try {
            String url = grafanaExploreUrlFactory.buildTraceByIdUrl(observabilityProperties, traceId.trim());
            return new GrafanaExploreUrlResponse(true, null, url);
        } catch (Exception e) {
            return new GrafanaExploreUrlResponse(true, "url_build_failed", null);
        }
    }
}
