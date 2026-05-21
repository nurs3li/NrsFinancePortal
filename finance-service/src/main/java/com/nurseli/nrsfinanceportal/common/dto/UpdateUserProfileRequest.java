package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Size(max = 128) String firstName,
        @Size(max = 128) String lastName
) {
}
