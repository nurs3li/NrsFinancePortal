package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskActionRequest(
        @NotBlank String action,
        Long accountId
) {
    // APPROVE, REJECT, TAKE_UNDER_MONITORING, SUGGEST_FREEZE
}