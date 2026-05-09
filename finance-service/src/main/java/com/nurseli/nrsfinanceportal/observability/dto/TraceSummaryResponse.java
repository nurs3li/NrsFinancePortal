package com.nurseli.nrsfinanceportal.observability.dto;

import java.util.Collections;
import java.util.List;

public record TraceSummaryResponse(
        boolean enabled,
        String disabledReason,
        boolean found,
        String traceId,
        List<TraceServiceNodeDto> nodes,
        List<TraceServiceEdgeDto> edges,
        String message
) {
    public static TraceSummaryResponse disabled(String reason) {
        return new TraceSummaryResponse(false, reason, false, null, Collections.emptyList(), Collections.emptyList(), reason);
    }

    public static TraceSummaryResponse notFound(String traceId) {
        return new TraceSummaryResponse(true, null, false, traceId, Collections.emptyList(), Collections.emptyList(), "Trace not found");
    }
}
