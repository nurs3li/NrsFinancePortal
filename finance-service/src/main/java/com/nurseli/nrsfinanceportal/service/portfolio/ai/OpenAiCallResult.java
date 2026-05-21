package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import java.util.Optional;

public record OpenAiCallResult(
        Optional<String> content,
        OpenAiFailureReason failureReason,
        long durationMs
) {
    public boolean success() {
        return content.isPresent();
    }
}
