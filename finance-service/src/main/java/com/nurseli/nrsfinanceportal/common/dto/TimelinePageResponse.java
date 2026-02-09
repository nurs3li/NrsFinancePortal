package com.nurseli.nrsfinanceportal.common.dto;

import java.util.List;

public record TimelinePageResponse(
        List<UnifiedTimelineDto> items,
        NextCursor nextCursor,
        boolean hasMore
) {
}
