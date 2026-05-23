package com.nurseli.nrsfinanceportal.api.admin;

import com.nurseli.nrsfinanceportal.config.ObservabilityProperties;
import com.nurseli.nrsfinanceportal.infrastructure.observability.GrafanaExploreUrlFactory;
import com.nurseli.nrsfinanceportal.infrastructure.observability.TempoTraceSummaryService;
import com.nurseli.nrsfinanceportal.infrastructure.observability.dto.GrafanaExploreUrlResponse;
import com.nurseli.nrsfinanceportal.infrastructure.observability.dto.TraceSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tempo trace özeti ve Grafana Explore deep-link endpoint'lerini admin rolü için sunar.
 */
@RestController
@RequestMapping("/api/admin/observability")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminObservabilityController {

    private final ObservabilityProperties observabilityProperties;
    private final TempoTraceSummaryService tempoTraceSummaryService;
    private final GrafanaExploreUrlFactory grafanaExploreUrlFactory;

    /**
     * {@code traceSummary} — Verilen {@code traceId} için Tempo üzerinden özet DTO döner.
     */
    @GetMapping("/traces/{traceId}/summary")
    public TraceSummaryResponse traceSummary(@PathVariable("traceId") String traceId) {
        return tempoTraceSummaryService.summarize(traceId);
    }

    /**
     * {@code exploreTrace} — Grafana'da trace'i açmak için hazır Explore URL'si üretir.
     */
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
