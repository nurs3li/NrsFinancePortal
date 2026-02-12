package com.nurseli.whaleanalytics.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionSnapshot(
        BigDecimal amount,
        Instant occurredAt
) {}
