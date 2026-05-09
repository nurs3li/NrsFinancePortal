package com.nurseli.nrsfinanceportal.observability.dto;

public record GrafanaExploreUrlResponse(boolean enabled, String disabledReason, String url) {
    public static GrafanaExploreUrlResponse disabled(String reason) {
        return new GrafanaExploreUrlResponse(false, reason, null);
    }
}
