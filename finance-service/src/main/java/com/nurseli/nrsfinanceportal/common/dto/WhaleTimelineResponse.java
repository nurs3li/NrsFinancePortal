package com.nurseli.nrsfinanceportal.common.dto;

import java.time.Instant;

public record WhaleTimelineResponse(
        Long id,
        Long userId,
        String whaleLevel,
        String reason,
        Instant triggeredAt,
        Instant createdAt
) {}