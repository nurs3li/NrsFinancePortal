package com.nurseli.nrsfinanceportal.integration.kafka.event;

import com.nurseli.nrsfinanceportal.domain.whale.WhaleLevel;
import java.time.Instant;

public record WhaleAlertTriggeredEvent(
        String userId,
        WhaleLevel level,
        long dailyVolume,
        int hourlyCount,
        long maxSingleTx,
        Instant triggeredAt
) {}
