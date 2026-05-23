package com.nurseli.nrsfinanceportal.infrastructure.observability.dto;

/**
 * Grafana Explore URL yanıt DTO.
 */
public record GrafanaExploreUrlResponse(boolean enabled, String disabledReason, String url) {
    public static GrafanaExploreUrlResponse disabled(String reason) {
        return new GrafanaExploreUrlResponse(false, reason, null);
    }
}
