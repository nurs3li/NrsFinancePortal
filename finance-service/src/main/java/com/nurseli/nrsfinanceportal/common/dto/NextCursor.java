package com.nurseli.nrsfinanceportal.common.dto;

import java.time.Instant;

public record NextCursor(
        Instant cursorAt,
        Long cursorId
) {
}
