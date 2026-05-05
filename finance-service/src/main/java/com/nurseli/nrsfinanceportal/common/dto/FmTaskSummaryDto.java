package com.nurseli.nrsfinanceportal.common.dto;

public record FmTaskSummaryDto(
        long poolOpenCount,
        long myClaimedOpenCount,
        long myCompletedCount
) {}
